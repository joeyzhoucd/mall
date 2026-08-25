package com.mall.coupon.schedule;

import com.mall.common.constant.SeckillMessageStatus;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.service.SeckillGrabService;
import com.mall.coupon.service.SeckillLocalMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 秒杀对账/清理任务：给 grab() 那个 try/catch 补一道保险——try/catch 只能兜住 Java
 * 异常，兜不住进程被物理杀掉（OOM Killer、K8s 驱逐、断电）这种情况。真出现这种崩溃，
 * Redis 那边"库存-1、用户已抢"的状态可能永远不会被回滚，也可能数据库里的记录卡在
 * 半途再也没人推进——这个任务定期扫一遍，把这几种"卡住"的状态修复掉。
 * <p>
 * mall-coupon 会有多个副本，每个副本都会独立触发这个 @Scheduled 方法，所以整个方法体
 * 包在一把 Redis 分布式锁里，同一时刻只让一个副本真正执行——不然两个副本同时发现
 * 同一条"孤儿记录"，各自都去把库存加回去，会把一份泄漏补偿成两份，反而制造超卖。
 */
@Component
public class SeckillReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillReconciliationTask.class);

    private static final String LOCK_KEY = "seckill:reconcile:lock";
    private static final long LOCK_TTL_MILLIS = 55_000;

    /** 对账扫描间隔本身就是"孤儿 Redis 记录"判定的宽限期——正常一次抢购从 Lua 到落库
     * 也就是毫秒级，远小于这个间隔，所以扫到的孤儿基本不会跟正常在途请求混淆。 */
    private static final long SWEEP_INTERVAL_MILLIS = 60_000;
    private static final long PENDING_WITH_ADDR_GRACE_MILLIS = 15_000;
    private static final long ABANDONED_PENDING_GRACE_MILLIS = 30 * 60_000L;
    private static final long STUCK_SENT_GRACE_MILLIS = 30_000;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeckillLocalMessageService seckillLocalMessageService;

    @Autowired
    private SeckillGrabService seckillGrabService;

    private final RedisScript<Long> unlockScript = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long.class);

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, initialDelay = 30_000)
    public void reconcile() {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY, token, Duration.ofMillis(LOCK_TTL_MILLIS));
        if (!Boolean.TRUE.equals(locked)) {
            // 别的副本正在跑，这一轮什么都不做，等下一轮。
            return;
        }
        try {
            reconcileOrphanRedisMembers();
            resumeStuckPendingWithAddress();
            expireAbandonedPending();
            resendStuckSent();
        } catch (Exception e) {
            log.error("秒杀对账任务本轮执行异常", e);
        } finally {
            redisTemplate.execute(unlockScript, Collections.singletonList(LOCK_KEY), token);
        }
    }

    /**
     * Redis 的 userKey 集合里有这个人，但 sms_seckill_local_message 里连一行记录都没有——
     * 说明 Lua 扣完库存之后、写库之前，进程就没了。这份库存名额已经没有任何东西可以
     * "续"，直接还给库存池。
     */
    private void reconcileOrphanRedisMembers() {
        Set<String> userKeys = scanKeys("seckill:user:*");
        for (String userKey : userKeys) {
            Long relationId = parseRelationId(userKey, "seckill:user:");
            if (relationId == null) {
                continue;
            }
            Set<String> members = redisTemplate.opsForSet().members(userKey);
            if (members == null || members.isEmpty()) {
                continue;
            }
            for (String memberIdStr : members) {
                Long memberId = parseLong(memberIdStr);
                if (memberId == null) {
                    continue;
                }
                SeckillLocalMessageEntity row = seckillLocalMessageService.getByRelationAndMember(relationId, memberId);
                if (row == null) {
                    seckillGrabService.releaseRedisHold(relationId, memberId);
                    log.warn("对账：relationId={} memberId={} 在 Redis 抢购名单里但本地消息表没有记录，已释放库存名额", relationId, memberId);
                }
            }
        }
    }

    /**
     * 状态还是 PENDING、地址已经有了、但超过宽限期还没发出去——大概率是查完地址、
     * 正要发 MQ 之前进程崩了。重新走一遍发送。
     */
    private void resumeStuckPendingWithAddress() {
        Date threshold = new Date(System.currentTimeMillis() - PENDING_WITH_ADDR_GRACE_MILLIS);
        List<SeckillLocalMessageEntity> stale = seckillLocalMessageService.findStaleReadyToSend(threshold);
        for (SeckillLocalMessageEntity candidate : stale) {
            // 处理前重新查一次最新状态：候选列表是查询时刻的快照，这段时间里
            // 如果用户自己的正常请求已经把流程走完了，这里就不该再插手。
            SeckillLocalMessageEntity fresh = seckillLocalMessageService.getById(candidate.getId());
            if (fresh == null || fresh.getStatus() == null
                    || fresh.getStatus() != SeckillMessageStatus.PENDING
                    || fresh.getAddrId() == null) {
                continue;
            }
            boolean sent = seckillGrabService.resendPendingMessage(fresh);
            log.info("对账：补发卡住的 PENDING 消息 messageId={} relationId={} memberId={} 结果={}",
                    fresh.getId(), fresh.getRelationId(), fresh.getMemberId(), sent ? "成功" : "失败");
        }
    }

    /**
     * 状态是 PENDING、地址一直是空、超过很久（对齐订单超时时长）——用户抢到之后
     * 再也没回来选地址，判定放弃，释放库存名额。
     */
    private void expireAbandonedPending() {
        Date threshold = new Date(System.currentTimeMillis() - ABANDONED_PENDING_GRACE_MILLIS);
        List<SeckillLocalMessageEntity> stale = seckillLocalMessageService.findAbandonedPending(threshold);
        for (SeckillLocalMessageEntity candidate : stale) {
            // markExpiredIfPending 自带 status=PENDING 的条件更新，返回 false 说明
            // 这期间用户已经自己把地址填了甚至建单了，不该再释放库存。
            boolean expired = seckillLocalMessageService.markExpiredIfPending(candidate.getId());
            if (expired) {
                seckillGrabService.releaseRedisHold(candidate.getRelationId(), candidate.getMemberId());
                log.info("对账：messageId={} relationId={} memberId={} 超过 {} 分钟未选地址，已过期并释放库存",
                        candidate.getId(), candidate.getRelationId(), candidate.getMemberId(),
                        ABANDONED_PENDING_GRACE_MILLIS / 60_000);
            }
        }
    }

    /**
     * 状态已经是 SENT（MQ confirm 真的成功过），但迟迟没等到 mall-order 回填
     * order_sn——重新发一遍，mall-order 那边靠 orderSn 幂等，不会重复建单。
     */
    private void resendStuckSent() {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_SENT_GRACE_MILLIS);
        List<SeckillLocalMessageEntity> stale = seckillLocalMessageService.findStaleSent(threshold);
        for (SeckillLocalMessageEntity candidate : stale) {
            SeckillLocalMessageEntity fresh = seckillLocalMessageService.getById(candidate.getId());
            if (fresh == null || fresh.getStatus() == null
                    || fresh.getStatus() != SeckillMessageStatus.SENT) {
                continue;
            }
            seckillGrabService.resendSentMessage(fresh);
            log.info("对账：messageId={} 状态卡在 SENT 太久，已补发一次", fresh.getId());
        }
    }

    private Set<String> scanKeys(String pattern) {
        Set<String> keys = new HashSet<>();
        redisTemplate.execute((RedisConnection connection) -> {
            try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(200).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return null;
        });
        return keys;
    }

    private Long parseRelationId(String key, String prefix) {
        try {
            return Long.valueOf(key.substring(prefix.length()));
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }
}

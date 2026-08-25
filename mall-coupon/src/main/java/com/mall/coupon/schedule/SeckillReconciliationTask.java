package com.mall.coupon.schedule;

import com.mall.common.constant.SeckillMessageStatus;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.service.SeckillGrabService;
import com.mall.coupon.service.SeckillLocalMessageService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀对账/清理任务：给 grab() 那个 try/catch 补一道保险——try/catch 只能兜住 Java
 * 异常，兜不住进程被物理杀掉（OOM Killer、K8s 驱逐、断电）这种情况。真出现这种崩溃，
 * Redis 那边"库存-1、用户已抢"的状态可能永远不会被回滚，也可能数据库里的记录卡在
 * 半途再也没人推进——这个任务定期扫一遍，把这几种"卡住"的状态修复掉。
 * <p>
 * mall-coupon 会有多个副本，每个副本都会独立触发这个 @Scheduled 方法，所以整个方法体
 * 包在一把分布式锁里，同一时刻只让一个副本真正执行——不然两个副本同时发现同一条
 * "孤儿记录"，各自都去把库存加回去，会把一份泄漏补偿成两份，反而制造超卖。用 Redisson
 * 的 RLock 而不是手写 SETNX+固定TTL：这一轮要扫多少条记录、要补发几次 MQ 事先不知道，
 * 执行时长不确定，Redisson 默认带 watchdog 自动续期（只要进程还活着就不会掉锁），
 * 比"猜一个够长的固定 TTL"更安全——手写版本如果这一轮扫描恰好跑得比 TTL 还久，
 * 锁会在跑到一半的时候过期，另一个副本会趁机跟着跑起来，正好复现这把锁本来要防的
 * 双重补偿问题。
 */
@Component
public class SeckillReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(SeckillReconciliationTask.class);

    private static final String LOCK_KEY = "seckill:reconcile:lock";

    private static final long SWEEP_INTERVAL_MILLIS = 60_000;
    private static final long PENDING_WITH_ADDR_GRACE_MILLIS = 15_000;
    private static final long ABANDONED_PENDING_GRACE_MILLIS = 30 * 60_000L;
    private static final long STUCK_SENT_GRACE_MILLIS = 30_000;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SeckillLocalMessageService seckillLocalMessageService;

    @Autowired
    private SeckillGrabService seckillGrabService;

    @Scheduled(fixedDelay = SWEEP_INTERVAL_MILLIS, initialDelay = 30_000)
    public void reconcile() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            // 不传 leaseTime，Redisson 会用它自己的 watchdog 机制自动续期；等锁的时间
            // 给 0——抢不到就说明别的副本正在跑，这一轮什么都不做，等下一轮，不用排队等。
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            reconcileOrphanRedisMembers();
            resumeStuckPendingWithAddress();
            expireAbandonedPending();
            resendStuckSent();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("秒杀对账任务本轮执行异常", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Redis 的 userKey 集合里有这个人，但 sms_seckill_local_message 里连一行记录都没有——
     * 说明 Lua 扣完库存之后、写库之前，进程就没了。这份库存名额已经没有任何东西可以
     * "续"，直接还给库存池。
     * <p>
     * 抢购成功的用户会一直留在 userKey 集合里（不能清掉，否则同一个人能重复抢），
     * 所以这个集合会随着这场秒杀累计卖出去的数量只增不减。这里按 relationId 批量查一次
     * "这场秒杀所有已经落库的 member_id"（getMemberIdsWithRecord），而不是每个 Redis
     * 成员单独查一次数据库，扫描成本只跟"当前 Redis 名单大小"和"这一轮真正的孤儿数"
     * 有关，不会随着历史累计成交量越滚越大。
     * <p>
     * 一个 Redis 成员要连续两轮扫描都判定是孤儿才会真正释放库存（借助 suspectKey 这个
     * Redis 集合记住上一轮的疑似名单）：doGrab() 里"落库之前"那一小段包含一次到
     * mall-member 的 Feign 调用，如果这个调用恰好很慢，一个正常在途的请求也可能在
     * 某一轮扫描时表现得跟孤儿一模一样。只信一轮判断不够谨慎；两轮加起来的等待时间
     * （这个方法本身 60 秒一轮）远超正常请求的处理时间，误判概率可以忽略不计。
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

            Set<Long> knownMemberIds = seckillLocalMessageService.getMemberIdsWithRecord(relationId);
            String suspectKey = suspectKey(relationId);
            Set<String> previousSuspects = redisTemplate.opsForSet().members(suspectKey);
            if (previousSuspects == null) {
                previousSuspects = java.util.Collections.emptySet();
            }

            Set<String> stillSuspect = new HashSet<>();
            for (String memberIdStr : members) {
                Long memberId = parseLong(memberIdStr);
                if (memberId == null || knownMemberIds.contains(memberId)) {
                    continue;
                }
                if (previousSuspects.contains(memberIdStr)) {
                    seckillGrabService.releaseRedisHold(relationId, memberId);
                    log.warn("对账：relationId={} memberId={} 连续两轮都在 Redis 抢购名单里但本地消息表没有记录，已释放库存名额",
                            relationId, memberId);
                } else {
                    stillSuspect.add(memberIdStr);
                }
            }

            redisTemplate.delete(suspectKey);
            if (!stillSuspect.isEmpty()) {
                redisTemplate.opsForSet().add(suspectKey, stillSuspect.toArray(new String[0]));
                redisTemplate.expire(suspectKey, Duration.ofMinutes(10));
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
            // markExpiredIfPending 自带 status=PENDING AND addr_id IS NULL 的条件更新，
            // 返回 false 说明这期间用户自己已经把地址填上了（哪怕状态还没来得及变成
            // SENT），不该再释放库存——见该方法的说明，这里是防止和 submitAddress()
            // 撞车导致一份库存卖两次的关键。
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

    private String suspectKey(Long relationId) {
        return "seckill:reconcile:suspect:" + relationId;
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

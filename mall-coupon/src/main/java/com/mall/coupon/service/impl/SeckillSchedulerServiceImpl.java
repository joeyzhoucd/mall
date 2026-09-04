package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.coupon.entity.SeckillSessionEntity;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.SeckillSchedulerService;
import com.mall.coupon.service.SeckillSessionService;
import com.mall.coupon.service.SeckillSkuRelationService;
import com.mall.coupon.vo.SeckillSchedulerVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

@Service
public class SeckillSchedulerServiceImpl implements SeckillSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SeckillSchedulerServiceImpl.class);

    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 场次名自动生成时用的前缀。管理员没填名字，也不该让他填。 */
    static final String AUTO_SESSION_PREFIX = "自动场次 ";

    @Autowired
    private SeckillSessionService seckillSessionService;

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 激活的实际动作复用 GrabService 的那一份实现 —— 它会把库存、限购键、
    // 商品信息缓存和跨 pod 广播按正确顺序做全。在这里重写一遍必然漏掉其中一步，
    // 而漏掉广播的表现是「别的 pod 上抢不到」，很难查。
    // 反向依赖不存在（GrabServiceImpl 不引用 SchedulerService），所以没有循环依赖。
    @Autowired
    private com.mall.coupon.service.SeckillGrabService seckillGrabService;

    /**
     * {@inheritDoc}
     *
     * <h3>为什么不自动激活</h3>
     * 「配置」和「上线」是两件事，这里只做前者。
     * <p>
     * {@code activate} 做两件有副作用的事：把 DB 里的 seckillCount 拷进
     * {@code seckill:stock:{id}}，以及<b>删掉 {@code seckill:user:{id}}</b>
     * （谁已经抢过）。对一条全新的关系来说这是安全的（那两个 key 本来就不存在），
     * 但即便如此也不该自动做——场次还没开始就激活，等于提前开卖。
     * <p>
     * 对一条<b>已激活</b>的关系就更不能自动做了：删掉「谁已抢过」意味着
     * 已经抢中的人可以再抢一次，超卖立刻发生。所以下面对这种情况直接拒绝改库存，
     * 错误信息和 {@code SeckillSkuRelationController#update} 保持一致 ——
     * 同一条规则不该有两套说法。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SeckillSchedulerVo vo) {
        if (vo == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (vo.getSkuId() == null) {
            throw new IllegalArgumentException("skuId 不能为空");
        }
        LocalDateTime start = parseTime(vo.getStartTime(), "开始时间");
        LocalDateTime end = parseTime(vo.getEndTime(), "结束时间");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
        if (isNotPositive(vo.getSeckillPrice())) {
            throw new IllegalArgumentException("秒杀价必须大于 0");
        }
        if (isNotPositive(vo.getSeckillCount())) {
            throw new IllegalArgumentException("秒杀总量必须大于 0");
        }
        if (isNotPositive(vo.getSeckillLimit())) {
            throw new IllegalArgumentException("每人限购必须大于 0");
        }
        if (vo.getSeckillLimit().compareTo(vo.getSeckillCount()) > 0) {
            // 限购比总量还大是没有意义的配置，而且会让「限购」这个词在页面上失去含义。
            throw new IllegalArgumentException("每人限购不能大于秒杀总量");
        }

        Long sessionId = findOrCreateSession(start, end);
        return upsertRelation(sessionId, vo);
    }

    /**
     * 同一个时间段复用同一个场次。
     * <p>
     * 不复用的话，后台每配一个 SKU 就会造一个新场次，
     * {@code sms_seckill_session} 很快变成一堆时间完全相同的重复行，
     * 而前台按场次分组展示的秒杀页会把本该在一起的商品拆成好几组。
     */
    private Long findOrCreateSession(LocalDateTime start, LocalDateTime end) {
        Date startDate = toDate(start);
        Date endDate = toDate(end);
        SeckillSessionEntity existing = seckillSessionService.getOne(
                new QueryWrapper<SeckillSessionEntity>()
                        .eq("start_time", startDate)
                        .eq("end_time", endDate)
                        .last("limit 1"),
                false);
        if (existing != null) {
            return existing.getId();
        }
        SeckillSessionEntity session = new SeckillSessionEntity();
        session.setName(AUTO_SESSION_PREFIX + start.format(TIME_FORMAT));
        session.setStartTime(startDate);
        session.setEndTime(endDate);
        session.setStatus(1);
        session.setCreateTime(new Date());
        seckillSessionService.save(session);
        log.info("秒杀配置：新建场次 id={} {} ~ {}", session.getId(), start.format(TIME_FORMAT), end.format(TIME_FORMAT));
        return session.getId();
    }

    /**
     * 同一个场次里同一个 SKU 只应有一条关系，所以是 upsert 而不是 insert。
     * <p>
     * 重复插入的后果不只是数据脏：每条关系在 Redis 里有自己的
     * {@code seckill:stock:{relationId}}，同一个商品出现两条就是两份独立库存，
     * 前台会显示两个一模一样的秒杀条目，各自能抢。
     */
    private Long upsertRelation(Long sessionId, SeckillSchedulerVo vo) {
        SeckillSkuRelationEntity existing = seckillSkuRelationService.getOne(
                new QueryWrapper<SeckillSkuRelationEntity>()
                        .eq("promotion_session_id", sessionId)
                        .eq("sku_id", vo.getSkuId())
                        .last("limit 1"),
                false);

        if (existing == null) {
            SeckillSkuRelationEntity relation = new SeckillSkuRelationEntity();
            relation.setPromotionSessionId(sessionId);
            relation.setSkuId(vo.getSkuId());
            relation.setSeckillPrice(vo.getSeckillPrice());
            relation.setSeckillCount(vo.getSeckillCount());
            relation.setSeckillLimit(vo.getSeckillLimit());
            relation.setSeckillSort(vo.getSeckillSort() == null ? 0 : vo.getSeckillSort());
            relation.setSoldCount(0);
            seckillSkuRelationService.save(relation);
            log.info("秒杀配置：新建关系 id={} sessionId={} skuId={}（尚未激活）",
                    relation.getId(), sessionId, vo.getSkuId());
            return relation.getId();
        }

        // 已经存在：只有在「还没激活」时才允许改库存。判据是 Redis 里那个 key 在不在 ——
        // 它是活动是否已经上线的唯一事实来源（DB 里的 seckill_count 只是配置值）。
        boolean countChanged = vo.getSeckillCount().compareTo(existing.getSeckillCount()) != 0;
        if (countChanged && isActivated(existing.getId())) {
            throw new IllegalArgumentException(
                    "这个秒杀已经激活，不能从这里改秒杀总量。秒杀库存的真实来源是 Redis，"
                            + "数据库里的值要通过 POST /coupon/seckill/activate/{relationId} 才会生效；"
                            + "而 activate 会清空「谁已抢过」的记录，活动进行中执行会导致超卖。"
                            + "请在活动未开始时调整。");
        }

        SeckillSkuRelationEntity patch = new SeckillSkuRelationEntity();
        patch.setId(existing.getId());
        patch.setSeckillPrice(vo.getSeckillPrice());
        patch.setSeckillLimit(vo.getSeckillLimit());
        if (vo.getSeckillSort() != null) {
            patch.setSeckillSort(vo.getSeckillSort());
        }
        if (countChanged) {
            patch.setSeckillCount(vo.getSeckillCount());
        }
        seckillSkuRelationService.updateById(patch);
        log.info("秒杀配置：更新关系 id={} sessionId={} skuId={} 改库存={}",
                existing.getId(), sessionId, vo.getSkuId(), countChanged);
        return existing.getId();
    }

    /** 活动是否已经上线：以 Redis 里的库存键为准，不看数据库。 */
    boolean isActivated(Long relationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("seckill:stock:" + relationId));
    }

    /**
     * {@inheritDoc}
     *
     * 这里刻意<b>不做</b>「开始时间还没到就拒绝」的检查。
     * 秒杀必须在开始前把库存预热进 Redis —— 压测实测过，冷启动那一下
     * 才是真正的瓶颈（见 mall-deploy 的压测记录）。
     * 开卖时间由前台按场次时间判断，不靠「什么时候激活」来控制。
     */
    @Override
    public void activate(Long relationId) {
        if (relationId == null) {
            throw new IllegalArgumentException("relationId 不能为空");
        }
        SeckillSkuRelationEntity relation = seckillSkuRelationService.getById(relationId);
        if (relation == null) {
            throw new IllegalArgumentException("秒杀配置不存在：" + relationId);
        }
        if (isActivated(relationId)) {
            // 这段话和 save() 里拒绝改库存时用的是同一个道理，措辞刻意保持一致。
            throw new IllegalStateException(
                    "这场秒杀已经上线了，不能再激活一次：激活会把库存重置回 "
                            + relation.getSeckillCount()
                            + " 并清空每人限购记录，已经抢中的人可以再抢一次，会直接超卖。"
                            + "确实要重来一轮请走运维通道。");
        }

        boolean ok = seckillGrabService.activate(relationId);
        if (!ok) {
            // activate 返回 false 只有一种情况：关系不存在或 seckillCount 为空。
            // 上面已经查过关系存在，所以走到这里说明 seckillCount 是空的。
            throw new IllegalStateException("激活失败：这条秒杀配置没有设置秒杀总量");
        }
        log.info("秒杀激活：relationId={} skuId={} 库存={}",
                relationId, relation.getSkuId(), relation.getSeckillCount());
    }

    private static boolean isNotPositive(BigDecimal v) {
        return v == null || v.compareTo(BigDecimal.ZERO) <= 0;
    }

    private static LocalDateTime parseTime(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        try {
            return LocalDateTime.parse(raw.trim(), TIME_FORMAT);
        } catch (DateTimeParseException e) {
            // 把期望格式写进错误信息里。前端如果漏了 value-format，传来的会是
            // "2026-09-03T02:00:00.000Z" 这种 ISO 串，报错要能一眼看出是格式问题。
            throw new IllegalArgumentException(
                    label + "格式不对，期望 yyyy-MM-dd HH:mm:ss，收到：" + raw);
        }
    }

    /**
     * 用系统默认时区转换。解析（无时区的 LocalDateTime）和这里的转换用同一个时区，
     * MyBatis 写 datetime 列时再按同一时区折算回去，一进一出抵消 ——
     * 落库的墙上时间和管理员填的完全一致，与容器时区是 UTC 还是 CST 无关。
     */
    private static Date toDate(LocalDateTime ldt) {
        return Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
    }
}

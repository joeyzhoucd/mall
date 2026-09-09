package com.mall.coupon.service.impl;

import com.mall.common.constant.ErrorCode;
import com.mall.common.metrics.BusinessFlow;
import com.mall.common.metrics.BusinessMetrics;
import com.mall.coupon.dao.CouponDao;
import com.mall.coupon.dao.CouponHistoryDao;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.entity.CouponHistoryEntity;
import com.mall.coupon.service.CouponClaimService;
import com.mall.coupon.service.CouponClaimTxOps;
import com.mall.coupon.service.CouponSoldOutException;
import com.mall.coupon.vo.MemberCouponVo;
import com.mall.coupon.vo.PromotionCouponVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 领券 / 用券。
 *
 * <h3>这个类里没有一把锁，这是刻意的</h3>
 * <ul>
 *   <li><b>总量不超发</b> -> 纯数据库：{@code CouponDao.tryReserveOne} 的条件更新。
 *       <b>实测</b>（2026-09-08，集群真库，50 并发抢 10 张）：恰好 10 个 OK、
 *       40 个 SOLD_OUT，{@code receive_count} 恰好 10。
 *       把它换成朴素的"先查再写"，同样条件下 <b>50 个全部成功</b>，
 *       而且 {@code receive_count} 因为丢更新只涨到 <b>2</b> ——
 *       账面写着发了 2 张，实际 50 个人手里有券。</li>
 *   <li><b>单人不超领</b> -> <b>唯一索引 + 这个类里的检查，两者缺一不可</b>。
 *       唯一索引 {@code uk_member_coupon_seq} 只保证一个 seq 槽位一个赢家；
 *       给 seq 定上限的是下面 {@code receive} 里的 {@code already >= perLimit}
 *       加上冲突后重新计数。实测去掉检查会从 2 张变 3 张，详见
 *       {@code CouponHistoryDao.countByMemberAndCoupon} 的注释。</li>
 *   <li><b>券不被重复使用</b> -> 纯数据库：{@code CouponHistoryDao.markUsed}
 *       的 {@code use_type = 0} 条件。</li>
 * </ul>
 * 应用层加 {@code synchronized} 或本地锁都没有意义 —— 服务是多副本部署，
 * JVM 锁跨不了进程。加分布式锁（项目里有 Redisson）也不必要：
 * 上面这套已经是强一致的，再套一把锁只是把吞吐压低。
 *
 * <h3>为什么不照抄秒杀那套 Redis + Lua</h3>
 * 项目里的秒杀（{@code SeckillGrabService}）是 Redis 信号量 + Lua 原子扣减 +
 * 本地消息表异步落库，还配了一个 {@code SeckillReconciliationTask} 对账。
 * 领券刻意<b>不用</b>它：
 * <table border="1">
 *   <tr><th></th><th>秒杀</th><th>领券</th></tr>
 *   <tr><td>量级</td><td>万级 QPS，DB 扛不住</td><td>促销页领券，DB 直接够</td></tr>
 *   <tr><td>一致性</td><td>可最终一致</td><td>超发即资金损失，要强一致</td></tr>
 *   <tr><td>代价</td><td>必须有对账任务补"Redis 扣了但库没落"</td><td>不需要对账</td></tr>
 * </table>
 * 同一类问题的两种解法，差别不在技术高低，在量级和一致性要求。
 * 这里少一个组件，也就少一类故障模式。
 */
@Service
public class CouponClaimServiceImpl implements CouponClaimService {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimServiceImpl.class);

    @Autowired
    private CouponDao couponDao;

    @Autowired
    private CouponHistoryDao couponHistoryDao;

    @Autowired
    private CouponClaimTxOps txOps;

    @Autowired
    private BusinessMetrics businessMetrics;

    @Override
    public List<PromotionCouponVo> promotions() {
        return couponDao.selectPromotionCoupons(new Date());
    }

    @Override
    public ClaimResult receive(Long couponId, Long memberId, String nickName) {
        if (memberId == null) {
            return failReceive(ErrorCode.COUPON_UNAUTHENTICATED);
        }
        if (couponId == null) {
            return failReceive(ErrorCode.COUPON_NOT_FOUND);
        }

        CouponEntity coupon = couponDao.selectById(couponId);
        ErrorCode eligibility = checkEligibility(coupon);
        if (eligibility != null) {
            return failReceive(eligibility);
        }

        int perLimit = coupon.getPerLimit() == null ? 1 : Math.max(1, coupon.getPerLimit());

        // ---------------------------------------------------------------
        // 有界重试：唯一索引冲突不等于"领满了"
        // ---------------------------------------------------------------
        // receive_seq 由「已领张数 + 1」算出，而这个 COUNT 和后面的 INSERT
        // 之间有窗口 —— 并发下两个请求可能都算出 seq=1，一个成功一个撞唯一索引。
        //
        // 【撞了不能直接返回失败】per_limit=2 时，第一次撞只说明 seq=1 被抢了，
        // seq=2 可能还空着。直接返回"已领取过"是错的（用户明明还能领一张）。
        // 所以重新数一次再试。
        //
        // 上界 perLimit + 2：每次冲突意味着有一个并发请求确实占掉了一个 seq，
        // 而 seq 最多 perLimit 个，所以 perLimit 次之内必然能确定"真的满了"。
        // +2 是给「并发请求的事务后来回滚、seq 又空出来」留的余量。
        // 【有界很重要】无界重试在热点券上会变成活锁，把线程全耗在这里。
        for (int attempt = 0; attempt < perLimit + 2; attempt++) {
            int already = couponHistoryDao.countByMemberAndCoupon(memberId, couponId);
            if (already >= perLimit) {
                return failReceive(ErrorCode.COUPON_PER_LIMIT_EXCEEDED);
            }
            try {
                Long historyId = txOps.claim(coupon, memberId, nickName, already + 1);
                businessMetrics.success(BusinessFlow.COUPON_RECEIVE);
                return new ClaimResult(ErrorCode.COUPON_RECEIVE_OK, historyId);
            } catch (DuplicateKeyException e) {
                // 这个 seq 被并发抢走了，重新数一次。不记 metrics ——
                // 这不是一次失败，是同一次领取里的一轮重试。
                log.debug("领券 seq 冲突，重试 attempt={} couponId={} memberId={}", attempt, couponId, memberId);
            } catch (CouponSoldOutException e) {
                return failReceive(ErrorCode.COUPON_SOLD_OUT);
            }
        }
        // 重试用尽。绝大多数情况就是真的领满了；
        // 少数情况是热点券上运气极差，用户重试一次即可。
        log.info("领券重试用尽 couponId={} memberId={} perLimit={}", couponId, memberId, perLimit);
        return failReceive(ErrorCode.COUPON_PER_LIMIT_EXCEEDED);
    }

    /**
     * 领取资格：券本身能不能被领。
     *
     * @return null = 通过；否则是拒绝原因
     */
    private ErrorCode checkEligibility(CouponEntity coupon) {
        if (coupon == null) {
            return ErrorCode.COUPON_NOT_FOUND;
        }
        if (coupon.getPublish() == null || coupon.getPublish() != 1) {
            return ErrorCode.COUPON_NOT_PUBLISHED;
        }
        Date now = new Date();
        if (coupon.getEnableStartTime() != null && now.before(coupon.getEnableStartTime())) {
            return ErrorCode.COUPON_RECEIVE_WINDOW_CLOSED;
        }
        if (coupon.getEnableEndTime() != null && now.after(coupon.getEnableEndTime())) {
            return ErrorCode.COUPON_RECEIVE_WINDOW_CLOSED;
        }
        // 【失败关闭】会员等级体系目前不存在：ums_member_level 实测 0 行，
        // 也没有任何接口能查到某个会员的等级。于是这个限制无法校验。
        // 忽略它照发 = 等级专属券被所有人领走（不可撤回的资金损失）；
        // 当作不可领 = 活动暂不可用（零损失）。选后者。
        // CouponDao.selectPromotionCoupons 里也把这类券滤掉了，这里是防御纵深。
        if (coupon.getMemberLevel() != null && coupon.getMemberLevel() != 0) {
            log.warn("券要求会员等级但等级体系未实现，拒绝领取 couponId={} memberLevel={}",
                    coupon.getId(), coupon.getMemberLevel());
            return ErrorCode.COUPON_MEMBER_LEVEL_NOT_MATCH;
        }
        // 这里的已领完判断只是快速失败，省掉一次事务。
        // 【真正的判据是 tryReserveOne 的条件更新】—— 这个 read 和后面的
        // UPDATE 之间有窗口，靠它防超发是错的。
        if (coupon.getReceiveCount() != null && coupon.getPublishCount() != null
                && coupon.getReceiveCount() >= coupon.getPublishCount()) {
            return ErrorCode.COUPON_SOLD_OUT;
        }
        return null;
    }

    @Override
    public List<MemberCouponVo> myCoupons(Long memberId, Integer status) {
        if (memberId == null) {
            return List.of();
        }
        return couponHistoryDao.selectMemberCoupons(memberId, status, new Date());
    }

    @Override
    public List<MemberCouponVo> usableForAmount(Long memberId, BigDecimal orderAmount) {
        if (memberId == null || orderAmount == null) {
            return List.of();
        }
        return couponHistoryDao.selectUsableForAmount(memberId, orderAmount, new Date());
    }

    @Override
    public UseResult preview(Long historyId, Long memberId, BigDecimal orderAmount) {
        return validateForUse(historyId, memberId, orderAmount);
    }

    @Override
    public UseResult use(Long historyId, Long memberId, BigDecimal orderAmount, String orderSn, Long orderId) {
        UseResult validated = validateForUse(historyId, memberId, orderAmount);
        if (!validated.ok()) {
            businessMetrics.failure(BusinessFlow.COUPON_USE, reasonOf(validated.code()));
            return validated;
        }
        MemberCouponVo coupon = couponHistoryDao.selectOwnedCoupon(historyId, memberId);
        if (coupon == null) {
            // 校验通过之后又查不到了 —— 只可能是并发删除，极少见但要有明确结果
            businessMetrics.failure(BusinessFlow.COUPON_USE, reasonOf(ErrorCode.COUPON_NOT_OWNED));
            return UseResult.fail(ErrorCode.COUPON_NOT_OWNED);
        }
        if (!txOps.use(historyId, memberId, coupon.getCouponId(), orderSn, orderId)) {
            // 条件更新没改到行：校验和这一步之间被另一笔并发订单抢先用掉了。
            // 【这一分支必须让下单失败】—— 忽略它就是一张券抵扣两笔订单。
            log.info("用券失败：券已被并发订单用掉 historyId={} orderSn={}", historyId, orderSn);
            businessMetrics.failure(BusinessFlow.COUPON_USE, reasonOf(ErrorCode.COUPON_ALREADY_USED));
            return UseResult.fail(ErrorCode.COUPON_ALREADY_USED);
        }
        businessMetrics.success(BusinessFlow.COUPON_USE);
        log.info("用券成功 historyId={} couponId={} discount={} orderSn={}",
                historyId, coupon.getCouponId(), validated.discount(), orderSn);
        return validated;
    }

    /**
     * 用券的服务端校验。{@code preview} 和 {@code use} 共用，保证两者判据完全一致 ——
     * 分开写迟早会漂移，而漂移的表现是"预览说能用，提交说不能用"。
     *
     * <p><b>客户端提交的 historyId 不可信</b>，归属判断在 SQL 的 WHERE 里
     * （{@code selectOwnedCoupon} 带 {@code member_id = ?}），不是查回来再比 ——
     * 后者容易被后来的改动漏掉。
     */
    private UseResult validateForUse(Long historyId, Long memberId, BigDecimal orderAmount) {
        if (historyId == null || memberId == null) {
            return UseResult.fail(ErrorCode.COUPON_NOT_OWNED);
        }
        if (orderAmount == null || orderAmount.signum() < 0) {
            return UseResult.fail(ErrorCode.COUPON_THRESHOLD_NOT_MET);
        }
        MemberCouponVo coupon = couponHistoryDao.selectOwnedCoupon(historyId, memberId);
        // 【券不存在和券不属于此人返回同一个码，是刻意的】
        // 分开返回等于给攻击者一个探测器：拿一批 id 试一遍就能枚举出
        // 哪些 historyId 是真实存在的（进而推断发券量、活动规模）。
        if (coupon == null) {
            return UseResult.fail(ErrorCode.COUPON_NOT_OWNED);
        }
        if (coupon.getStatus() != null && coupon.getStatus() == 1) {
            return UseResult.fail(ErrorCode.COUPON_ALREADY_USED);
        }
        if (coupon.isExpired()) {
            return UseResult.fail(ErrorCode.COUPON_EXPIRED);
        }
        // 【失败关闭】适用范围 1（指定分类）/ 2（指定商品）需要知道订单里有哪些
        // spu 和分类，而 OrderServiceImpl.buildOrderItem 从来不填 oms_order_item
        // 的 spu_id / category_id（实测三列全 NULL），购物车的 OrderItemVo 也只有 skuId。
        // 忽略范围照抵扣 = 指定商品券能抵扣任意商品（直接的资金损失）；
        // 当作不可用 = 这类券暂时用不了（零损失）。选后者。
        if (coupon.getScopeType() == null || coupon.getScopeType() != 0) {
            return UseResult.fail(ErrorCode.COUPON_SCOPE_NOT_MATCH);
        }
        BigDecimal threshold = coupon.getMinPoint() == null ? BigDecimal.ZERO : coupon.getMinPoint();
        if (orderAmount.compareTo(threshold) < 0) {
            return UseResult.fail(ErrorCode.COUPON_THRESHOLD_NOT_MET);
        }
        BigDecimal face = coupon.getAmount() == null ? BigDecimal.ZERO : coupon.getAmount();
        // 【抵扣不能超过订单金额】券面 100 用在 60 元订单上只减 60。
        // 少了这个 min，pay_amount 会变成负数 —— 而 decimal(18,4) 存得下负数，
        // 不会有任何报错，直到对账时才发现。
        BigDecimal discount = face.min(orderAmount);
        return new UseResult(ErrorCode.COUPON_RECEIVE_OK, discount);
    }

    @Override
    public boolean release(Long historyId, String orderSn) {
        if (historyId == null || orderSn == null) {
            log.warn("退券参数不合法，跳过（这张券不会被退回）historyId={} orderSn={}", historyId, orderSn);
            return false;
        }
        // 退券只需要 couponId 来回退 use_count，不做归属校验 ——
        // 补偿是系统内部动作，调用方是 mall-order 而不是浏览器。
        // 真正的安全限定在 markUnusedByOrder 的 WHERE 里（带 order_sn）。
        CouponHistoryEntity entity = couponHistoryDao.selectById(historyId);
        if (entity == null) {
            log.warn("退券找不到记录，跳过 historyId={} orderSn={}", historyId, orderSn);
            return false;
        }
        return txOps.release(historyId, entity.getCouponId(), orderSn);
    }

    private ClaimResult failReceive(ErrorCode code) {
        businessMetrics.failure(BusinessFlow.COUPON_RECEIVE, reasonOf(code));
        return new ClaimResult(code, null);
    }

    /**
     * 把 ErrorCode 转成 metrics 的 reason 标签。
     *
     * <p>直接用枚举名而不是另立一套常量：另立一套迟早和 ErrorCode 漂移，
     * 而漂移的方向是"新加的失败原因忘了打点"，也就是压测时看不见。
     * <p>
     * 代价是<b>重命名 ErrorCode 会静默改掉指标标签</b>，让已有的面板和告警失效。
     * 这一点由 {@code CouponErrorCodeMetricsTest} 钉住：
     * 它断言这批码的名字集合恰好等于预期集合，改名会让测试失败而不是让面板悄悄空掉。
     * <p>
     * 基数是有界的（一个固定枚举），不会造成指标爆炸。
     */
    private String reasonOf(ErrorCode code) {
        return code.name().toLowerCase();
    }
}

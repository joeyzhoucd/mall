package com.mall.coupon.service;

import com.mall.common.constant.ErrorCode;
import com.mall.coupon.vo.MemberCouponVo;
import com.mall.coupon.vo.PromotionCouponVo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券的"领取"和"使用"。
 *
 * <p>和 {@link CouponService} / {@link CouponHistoryService} 的关系：
 * 那两个是代码生成器产出的后台 CRUD（{@code list/info/save/update/delete}），
 * 这个才是业务入口。分开是因为两者的信任域完全不同 ——
 * CRUD 走后台管理员令牌，这里走前台会员会话。
 */
public interface CouponClaimService {

    /**
     * 领券的结果。
     *
     * <p>用 {@link ErrorCode} 而不是布尔值，因为<b>每种失败必须能被区分</b>：
     * 领券压测要断言"并发 200 抢 10 张 = 恰好 10 个成功 + 190 个已领完"，
     * 如果把"券发完了"和"这个人领满了"混成一个码，就验证不了限领那一维。
     *
     * @param historyId 成功时是新领到那张券的 {@code sms_coupon_history.id}，失败时为 null
     */
    record ClaimResult(ErrorCode code, Long historyId) {
        public boolean ok() {
            return code == ErrorCode.COUPON_RECEIVE_OK;
        }

        public static ClaimResult fail(ErrorCode code) {
            return new ClaimResult(code, null);
        }
    }

    /**
     * 用券/预览的结果。
     *
     * @param discount 实际抵扣金额。<b>由服务端算</b>，绝不采信客户端传来的值。
     *                 且不会超过订单金额（券面 100 用在 60 元订单上只减 60，不会出现负数应付）。
     */
    record UseResult(ErrorCode code, BigDecimal discount) {
        public boolean ok() {
            return code == ErrorCode.COUPON_RECEIVE_OK;
        }

        public static UseResult fail(ErrorCode code) {
            return new UseResult(code, BigDecimal.ZERO);
        }
    }

    /** 促销页的券列表（已发布、在领取窗口内；已抢光的也返回，前端显示"已抢光"）。 */
    List<PromotionCouponVo> promotions();

    /**
     * 领一张券。
     *
     * @param memberId 来自服务端会话，<b>不是客户端参数</b>
     */
    ClaimResult receive(Long couponId, Long memberId, String nickName);

    /**
     * "我的优惠券"。
     *
     * @param status null = 全部；0 = 可用（未使用且未过期）；1 = 已使用；2 = 已过期
     */
    List<MemberCouponVo> myCoupons(Long memberId, Integer status);

    /** 结算页可选的券：未使用、未过期、且订单金额达到门槛。 */
    List<MemberCouponVo> usableForAmount(Long memberId, BigDecimal orderAmount);

    /**
     * 预览抵扣金额 —— <b>只读，不改任何状态</b>。
     *
     * <p>下单流程里 mall-order 先调它算出应付金额做价格校验，再调 {@link #use}。
     * 拆成两步是刻意的：价格变动、库存不足这些<b>常见</b>失败都发生在
     * 券状态被改动<b>之前</b>，于是不需要走补偿路径。
     * 只有"券已占用但订单落库失败"这种少见情况才需要 {@link #release}。
     * <p>
     * 补偿路径是整条链上最脆弱的一环（这个仓库已经被一个会抛异常的补偿逻辑
     * 坑过一次，导致锁定库存永远不释放），所以要尽量少走。
     */
    UseResult preview(Long historyId, Long memberId, BigDecimal orderAmount);

    /**
     * 用券：校验 + 标记已使用 + 绑定订单号。由 mall-order 通过 Feign 调用。
     *
     * <p>这里会把 {@link #preview} 的校验<b>全部重做一遍</b>，不是冗余：
     * 两次调用之间券可能已经被另一笔并发订单用掉，也可能刚好过期。
     * 而且客户端能提交任意 {@code historyId}，服务端必须自己判断归属。
     */
    UseResult use(Long historyId, Long memberId, BigDecimal orderAmount, String orderSn, Long orderId);

    /**
     * 退券补偿。幂等 —— 重复调用第二次返回 false 并被安全忽略。
     *
     * @param orderSn 必传。只按 historyId 退会退错券，见
     *                {@code CouponHistoryDao.markUnusedByOrder} 的注释。
     */
    boolean release(Long historyId, String orderSn);
}

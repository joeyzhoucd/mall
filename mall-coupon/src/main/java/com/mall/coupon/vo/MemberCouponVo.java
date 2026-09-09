package com.mall.coupon.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 会员手里的一张券（"我的优惠券"、结算页可选券、以及用券前的服务端校验都用它）。
 *
 * <p>{@code id} 是 {@code sms_coupon_history.id}，也就是"这一张券"；
 * {@code couponId} 才是券的种类。结算时前端提交的是 {@code id}，不是 {@code couponId} ——
 * 一个人可能持有同一种券的多张（{@code per_limit > 1}）。
 *
 * <h3>命名对齐</h3>
 * 适用范围叫 {@code scopeType}，使用状态叫 {@code status}。
 * 原因见 {@link PromotionCouponVo} 类注释里的命名警告：
 * 两张表的列都叫 {@code use_type} 而含义相反，join 之后同名会静默互相覆盖。
 */
@Data
public class MemberCouponVo {

    /** sms_coupon_history.id —— 就是"这一张券"，结算时提交这个 */
    private Long id;

    /** sms_coupon.id —— 券的种类 */
    private Long couponId;

    private String couponName;

    private String couponImg;

    /** 面额 */
    private BigDecimal amount;

    /** 使用门槛，0 = 无门槛 */
    private BigDecimal minPoint;

    /** 适用范围：0 全场通用 / 1 指定分类 / 2 指定商品 */
    private Integer scopeType;

    /** 使用状态：0 未使用 / 1 已使用 / 2 已过期 */
    private Integer status;

    private Date createTime;

    /**
     * 领取时冻结的失效时间。
     * <p>
     * 快照自 {@code sms_coupon.end_time}，<b>不是</b>实时读券本体 ——
     * 否则运营在后台把 end_time 改早，已经发出去的券会追溯失效。
     */
    private Date expireTime;

    private Date useTime;

    private String orderSn;

    /**
     * 是否已过期。
     * <p>
     * 过期<b>不落库</b>，在读的时候算。这样不需要一个定时任务扫全表把
     * {@code use_type} 改成 2 —— 少一个组件、少一类故障模式（那个任务挂了
     * 会让过期券一直显示成可用，而且不会有任何报错）。
     * <p>
     * {@code status == 1}（已使用）优先：已经用掉的券即使超过 expireTime
     * 也该显示"已使用"而不是"已过期"，因为它确实被用在了一笔有效订单上。
     */
    public boolean isExpired() {
        if (status != null && status == 1) {
            return false;
        }
        return expireTime != null && expireTime.before(new Date());
    }

    /** 现在能不能用于结算：未使用 + 未过期。门槛和适用范围由服务端另外校验。 */
    public boolean isUsable() {
        return status != null && status == 0 && !isExpired();
    }
}

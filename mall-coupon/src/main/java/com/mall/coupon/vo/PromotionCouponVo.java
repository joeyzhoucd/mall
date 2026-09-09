package com.mall.coupon.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 促销页上一张可领取的券。
 *
 * <h3>【命名警告】{@code use_type} 这个列名在两张表里含义完全不同</h3>
 * <ul>
 *   <li>{@code sms_coupon.use_type} —— 券的<b>适用范围</b>：0 全场通用 / 1 指定分类 / 2 指定商品</li>
 *   <li>{@code sms_coupon_history.use_type} —— 领取记录的<b>使用状态</b>：0 未使用 / 1 已使用 / 2 已过期</li>
 * </ul>
 * 两张表要 join，如果 VO 里也叫 {@code useType}，一个 {@code SELECT c.use_type, h.use_type}
 * 就会让后一个覆盖前一个，而且<b>类型相同（都是 tinyint）、编译和运行都不会报错</b> ——
 * 表现是"全场券突然变成已过期"或"已用的券显示成可用"。
 * <p>
 * 所以这里刻意改名：适用范围叫 {@code scopeType}，使用状态叫 {@code status}
 * （见 {@link MemberCouponVo}）。XML 里的 {@code AS} 别名必须和这里对齐。
 */
@Data
public class PromotionCouponVo {

    private Long id;

    private String couponName;

    private String couponImg;

    /** 面额（减多少） */
    private BigDecimal amount;

    /** 使用门槛（满多少可用），0 = 无门槛 */
    private BigDecimal minPoint;

    /** 适用范围：0 全场通用 / 1 指定分类 / 2 指定商品。见类注释的命名警告。 */
    private Integer scopeType;

    /** 每人限领张数 */
    private Integer perLimit;

    private Integer publishCount;

    private Integer receiveCount;

    /** 券本身的有效期结束时间（领到手之后能用到什么时候） */
    private Date endTime;

    /** 领取窗口的结束时间（什么时候之前能领） */
    private Date enableEndTime;

    private String note;

    /**
     * 还剩多少张。
     * <p>
     * 在服务端算而不是让前端做 {@code publishCount - receiveCount}：
     * 前端算的话每个调用方都要重复一遍这个逻辑，而且一旦以后改成"按批次发行"，
     * 所有前端都要跟着改。
     */
    public int getRemaining() {
        if (publishCount == null || receiveCount == null) {
            return 0;
        }
        return Math.max(0, publishCount - receiveCount);
    }

    /** 是否已抢光。已抢光的券仍然返回给前端显示成"已抢光"，见 CouponDao.selectPromotionCoupons。 */
    public boolean isSoldOut() {
        return getRemaining() <= 0;
    }
}

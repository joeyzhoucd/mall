package com.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderSubmitVo {
    private Long addrId;
    private Integer payType;
    private String orderToken;
    private BigDecimal payPrice;
    private String note;

    /**
     * 用户选中的优惠券 —— {@code sms_coupon_history.id}，不是 couponId。
     *
     * <p>一个人可能持有同一种券的多张（{@code per_limit > 1}），
     * 所以必须指明"用哪一张"。
     *
     * <p><b>这个值完全不可信</b>：它来自浏览器，可以是任意数字。
     * 服务端在 mall-coupon 侧会重新校验归属（SQL 的
     * {@code WHERE member_id = ?}）、使用状态、过期时间和门槛，
     * 抵扣金额也由服务端算 —— {@link #payPrice} 只用来做"价格是否变动"的比对，
     * 从不作为扣款依据。
     */
    private Long couponHistoryId;
}

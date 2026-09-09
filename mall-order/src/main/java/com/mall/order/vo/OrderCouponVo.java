package com.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 结算页上一张可选的券。
 *
 * <p>这是 mall-coupon 的 {@code MemberCouponVo} 在 mall-order 侧的镜像。
 * <b>刻意复制而不是抽到 mall-common</b>：两边的字段需求会各自演化
 * （结算页不需要 useTime/orderSn，"我的优惠券"页不需要 discount），
 * 放进 mall-common 会让任何一侧的字段变更强制另一侧重新编译部署。
 * 项目里 {@code MemberAddressVo} 也是同样在三个服务里各有一份。
 *
 * <p>{@code id} 是 {@code sms_coupon_history.id}，也就是"这一张券"。
 * 提交订单时前端回传的就是它 —— 不是 couponId，因为一个人可能持有
 * 同一种券的多张（{@code per_limit > 1}）。
 */
@Data
public class OrderCouponVo {

    private Long id;

    private Long couponId;

    private String couponName;

    /** 面额 */
    private BigDecimal amount;

    /** 使用门槛，0 = 无门槛 */
    private BigDecimal minPoint;

    private Date expireTime;
}

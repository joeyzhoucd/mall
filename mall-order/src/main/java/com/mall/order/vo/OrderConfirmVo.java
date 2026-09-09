package com.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderConfirmVo {
    private List<MemberAddressVo> address = new ArrayList<>();
    private List<OrderItemVo> items = new ArrayList<>();
    private Integer integration = 0;
    private String orderToken;
    private BigDecimal freightAmount = BigDecimal.ZERO;

    /**
     * 当前订单金额下可用的优惠券。
     *
     * <p>门槛过滤由 mall-coupon 在 SQL 里做，所以这个列表里的券都是真能用的 ——
     * 让用户看到一张选不了的券、点了才报错是很差的交互。
     * <p>
     * 但<b>它只是给页面看的</b>：提交订单时后端会对客户端回传的
     * {@code couponHistoryId} 重新做一遍完整校验（归属、状态、过期、门槛）。
     * 客户端能提交任意 id，不能假设它只会提交这个列表里的。
     */
    private List<OrderCouponVo> coupons = new ArrayList<>();

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(OrderItemVo::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 应付金额（不含券）。
     *
     * <p><b>刻意不在这里减券</b>：结算页刚打开时还没选券，
     * 选了券之后的应付金额由前端即时算给用户看，
     * 而<b>真正的应付金额由服务端在 createOrder 里算</b>（见 OrderServiceImpl）。
     * 如果这里也算一份，就有两处计算逻辑，迟早漂移 ——
     * 而漂移的表现是"页面显示的应付和实际扣款不一致"。
     */
    public BigDecimal getPayAmount() {
        return getTotalAmount().add(freightAmount == null ? BigDecimal.ZERO : freightAmount);
    }
}

package com.mall.order.vo;

import com.mall.member.entity.MemberReceiveAddressEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class OrderConfirmVo {
    private List<MemberReceiveAddressEntity> address = new ArrayList<>();
    private List<OrderItemVo> items = new ArrayList<>();
    private Integer integration = 0;
    private String orderToken;
    private BigDecimal freightAmount = BigDecimal.ZERO;

    public BigDecimal getTotalAmount() {
        return items.stream()
                .map(OrderItemVo::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getPayAmount() {
        return getTotalAmount().add(freightAmount == null ? BigDecimal.ZERO : freightAmount);
    }
}


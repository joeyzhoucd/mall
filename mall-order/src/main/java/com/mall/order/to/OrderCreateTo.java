package com.mall.order.to;

import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderCreateTo {
    private OrderEntity order;
    private List<OrderItemEntity> orderItems;
    private BigDecimal payPrice;
}


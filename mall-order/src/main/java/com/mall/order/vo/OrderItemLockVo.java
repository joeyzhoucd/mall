package com.mall.order.vo;

import lombok.Data;

@Data
public class OrderItemLockVo {
    private Long skuId;
    private Integer count;
    private String title;
}


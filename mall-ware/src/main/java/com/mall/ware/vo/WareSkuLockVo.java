package com.mall.ware.vo;

import lombok.Data;

import java.util.List;

@Data
public class WareSkuLockVo {
    private String orderSn;
    private List<OrderItemLockVo> locks;
}


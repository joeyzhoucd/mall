package com.mall.common.to;

import lombok.Data;

@Data
public class StockReleaseItemTo {
    private String orderSn;
    private Long skuId;
    private Integer count;
}


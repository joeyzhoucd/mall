package com.mall.common.to;

import lombok.Data;

import java.util.List;

@Data
public class StockReleaseTo {
    private String orderSn;
    private List<StockReleaseItemTo> items;
}


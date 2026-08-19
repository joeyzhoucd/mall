package com.mall.ware.vo;

import lombok.Data;

@Data
public class StockFailVo {
    private Long taskDetailId;
    private Long skuId;
    private Integer skuNum;
    private Integer lockStatus;
    private Integer retryCount;
    private String orderSn;
}


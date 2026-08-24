package com.mall.coupon.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillPageVo {
    private Long relationId;
    private Long skuId;
    private String skuName;
    private String skuPic;
    private BigDecimal seckillPrice;
    private BigDecimal originalPrice;
    private Integer seckillCount;
    private Integer soldCount;
}

package com.mall.cart.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SkuInfoVo {
    private Long skuId;
    private Long spuId;
    private Long categoryId;
    private Long brandId;
    private String skuName;
    private String skuTitle;
    private String skuSubtitle;
    private String skuDefaultImg;
    private BigDecimal price;
}


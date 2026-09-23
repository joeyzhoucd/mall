package com.mall.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CartItemVo {
    private Long skuId;
    /**
     * 【为什么购物车项要存 spuId】行为埋点的共现统计必须在 SPU 粒度做
     * （SKU 是颜色/版本变体）。加购之后的改数量/删除等动作只读缓存里的条目，
     * 不会再调一次商品服务，所以 spuId 必须在这里缓存下来。
     * 顺带：购物车页要链到商品详情也用得上。
     * 老的缓存条目没有这个字段，反序列化后是 null，埋点会跳过它们。
     */
    private Long spuId;
    private String title;
    private String image;
    private List<String> skuAttr;
    private BigDecimal price;
    private Integer count;
    private Boolean check = true;

    public BigDecimal getTotalPrice() {
        if (price == null || count == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(new BigDecimal(count));
    }
}


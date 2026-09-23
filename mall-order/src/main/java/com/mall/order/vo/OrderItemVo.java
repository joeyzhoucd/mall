package com.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderItemVo {
    private Long skuId;
    /**
     * 从购物车条目（mall-cart 的 CartItemVo）反序列化过来。
     * 【可能为 null】加这两个字段之前就放进购物车的老条目没有它们。
     * 那种情况下 oms_order_item 的这两列留空，由
     * data-seed/migration-2026-09-23-order-item-spu-backfill.sql 事后按 sku 回填。
     */
    private Long spuId;
    private Long categoryId;
    private String title;
    private String image;
    private List<String> skuAttr;
    private BigDecimal price;
    private Integer count;

    public BigDecimal getTotalPrice() {
        if (price == null || count == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(new BigDecimal(count));
    }
}


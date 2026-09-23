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
    /**
     * 【购物车项带着类目，是为了让 mall-order 不必再调商品服务】
     * mall-order 建订单行时从购物车取条目（CartFeignService），
     * 它把 spuId / categoryId 原样写进 oms_order_item。
     * 2026-09-23 之前这两列在【全库 11071 个订单行里 100% 为空】——
     * buildOrderItem 从来没设过，而 mall-order 也没有通往商品服务的 Feign。
     * 另起一个 Feign 去查会在【提交订单】路径上加一个同步依赖，
     * 而这两个值加购时就已经拿到了（SkuInfoVo 里有），顺着带过去最便宜。
     */
    private Long categoryId;
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


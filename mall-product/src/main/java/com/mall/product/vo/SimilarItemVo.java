package com.mall.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 详情页推荐位的一条商品。
 *
 * <p>字段刻意只留详情页模板真正会用到的这几个，和 mall-search 那边
 * {@code SearchServiceImpl.SOURCE_FIELDS} 返回的字段对齐。
 * <b>不要图省事直接复用 SkuEsModel</b>：那个类带着 512 维的 titleVector，
 * 一旦被反序列化进来，每条推荐就多背 512 个浮点数，而页面一个都用不上。
 */
@Data
public class SimilarItemVo {

    private Long skuId;
    private String skuTitle;
    private BigDecimal skuPrice;
    private String skuImg;
    private Long saleCount;
}

package com.joeyzhoucd.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU信息VO
 * 用于前端展示，包含默认图片等前端需要的字段
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
public class SkuInfoVo {
    /**
     * skuId
     */
    private Long skuId;
    /**
     * spuId
     */
    private Long spuId;
    /**
     * sku名称
     */
    private String skuName;
    /**
     * sku介绍描述
     */
    private String skuDesc;
    /**
     * 所属分类id
     */
    private Long categoryId;
    /**
     * 品牌id
     */
    private Long brandId;
    /**
     * 默认图片
     */
    private String skuDefaultImg;
    /**
     * 标题
     */
    private String skuTitle;
    /**
     * 副标题
     */
    private String skuSubtitle;
    /**
     * 价格
     */
    private BigDecimal price;
    /**
     * 销量
     */
    private Long saleCount;
    /**
     * 分类名称
     */
    private String categoryName;
    /**
     * 品牌名称
     */
    private String brandName;
    /**
     * SKU图片列表
     */
    private List<SkuImageVo> images;
    /**
     * 销售属性列表
     */
    private List<SkuSaleAttrVo> saleAttrs;

    @Data
    public static class SkuImageVo {
        private Long id;
        private String imgUrl;
        private Integer imgSort;
        private Integer defaultImg;
    }

    @Data
    public static class SkuSaleAttrVo {
        private Long attrId;
        private String attrName;
        private String attrValue;
        private Integer attrSort;
    }
}

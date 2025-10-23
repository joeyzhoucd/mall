package com.mall.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;


@Data
public class SkuInfoVo {
    
    private Long skuId;
    
    private Long spuId;
    
    private String skuName;
    
    private String skuDesc;
    
    private Long categoryId;
    
    private Long brandId;
    
    private String skuDefaultImg;
    
    private String skuTitle;
    
    private String skuSubtitle;
    
    private BigDecimal price;
    
    private Long saleCount;
    
    private String categoryName;
    
    private String brandName;
    
    private List<SkuImageVo> images;
    
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

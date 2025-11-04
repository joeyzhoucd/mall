package com.mall.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用于上架的商品模型
 */
@Data
public class SkuEsModelVo {
    
    private Long skuId;
    
    private Long spuId;
    
    private String skuTitle;
    
    private BigDecimal skuPrice;
    
    private String skuImg;
    
    private Long saleCount;
    
    private Boolean hasStock;
    
    private Long hotScore;
    
    private Long brandId;
    
    private Long categoryId;
    
    private String brandName;
    
    private String brandImg;
    
    private String categoryName;
    
    private List<Attrs> attrs;
    
    @Data
    public static class Attrs {
        private Long attrId;
        private String attrName;
        private String attrValue;
    }
}


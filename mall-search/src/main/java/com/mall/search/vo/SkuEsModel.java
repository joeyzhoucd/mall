package com.mall.search.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Elasticsearch商品模型
 */
@Data
public class SkuEsModel {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long skuId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long spuId;
    
    private String skuTitle;
    
    private BigDecimal skuPrice;
    
    private String skuImg;
    
    private Long saleCount;
    
    private Boolean hasStock;
    
    private Long hotScore;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long brandId;
    
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    
    private String brandName;
    
    private String brandImg;
    
    private String categoryName;
    
    private List<Attrs> attrs;
    
    @Data
    public static class Attrs {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long attrId;
        private String attrName;
        private String attrValue;
    }
}


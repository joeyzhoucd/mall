package com.joeyzhoucd.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SpuSaveVo {
    private String spuName;
    private String spuDescription;
    private Long categoryId; // Changed from int to Long
    private Long brandId;   // Changed from int to Long
    private BigDecimal weight; // Changed from BigDecimal to BigDecimal
    private Integer publishStatus;
    private List<String> decript;
    private List<String> images;
    private Bounds bounds;
    private List<BaseAttrs> baseAttrs;
    private List<Skus> skus;

    @Data
    public static class Bounds {
        private Integer buyBounds; // Changed from int to Integer
        private Integer growBounds; // Changed from int to Integer
    }

    @Data
    public static class BaseAttrs {
        private Long attrId; // Changed from int to Long
        private String attrValues;
        private Integer showDesc; // Changed from int to Integer
    }

    @Data
    public static class Skus {
        private Long skuId; // Added for SKU ID
        private List<Attr> attr;
        private String skuName;
        private String skuTitle;
        private String skuSubtitle;
        private BigDecimal price; // Changed from String to BigDecimal
        private Integer stock;
        private String skuCode;
        private List<Images> images;
        private Integer fullCount;
        private BigDecimal discount; // Changed from String to BigDecimal
        private Integer countStatus;
        private BigDecimal fullPrice; // Changed from String to BigDecimal
        private BigDecimal reducePrice; // Changed from String to BigDecimal
        private Integer priceStatus;
        private List<MemberPrice> memberPrice;
    }

    @Data
    public static class Attr {
        private Long attrId; // Changed from int to Long
        private String attrName;
        private String attrValue;
    }

    @Data
    public static class Images {
        private String imgUrl;
        private Integer defaultImg; // Changed from int to Integer
    }

    @Data
    public static class MemberPrice {
        private Long id; // Changed from int to Long
        private String name;
        private BigDecimal price; // Changed from int to BigDecimal
    }
}

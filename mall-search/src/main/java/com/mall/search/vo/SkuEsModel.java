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

    /**
     * 商品标题的语义向量，上架时由 TEI 生成，供 kNN 检索使用。
     *
     * <p><b>搜索结果里这个字段永远是 null</b>，因为 SearchServiceImpl 的
     * {@code _source} 是白名单，只取展示需要的那几个字段。
     * 这是有意的：512 个浮点数按 JSON 文本传回来，每页 16 条就是 8000 多个数字，
     * 白白撑大响应体和反序列化开销，而页面一个都用不上。
     */
    private List<Float> titleVector;

    @Data
    public static class Attrs {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        private Long attrId;
        private String attrName;
        private String attrValue;
    }
}


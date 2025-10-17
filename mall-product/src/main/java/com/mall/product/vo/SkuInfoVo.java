package com.mall.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKUä¿¡æ¯VO
 * ç”¨äºŽå‰ç«¯å±•ç¤ºï¼ŒåŒ…å«é»˜è®¤å›¾ç‰‡ç­‰å‰ç«¯éœ€è¦çš„å­—æ®µ
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
     * skuåç§°
     */
    private String skuName;
    /**
     * skuä»‹ç»æè¿°
     */
    private String skuDesc;
    /**
     * æ‰€å±žåˆ†ç±»id
     */
    private Long categoryId;
    /**
     * å“ç‰Œid
     */
    private Long brandId;
    /**
     * é»˜è®¤å›¾ç‰‡
     */
    private String skuDefaultImg;
    /**
     * æ ‡é¢˜
     */
    private String skuTitle;
    /**
     * å‰¯æ ‡é¢˜
     */
    private String skuSubtitle;
    /**
     * ä»·æ ¼
     */
    private BigDecimal price;
    /**
     * é”€é‡
     */
    private Long saleCount;
    /**
     * åˆ†ç±»åç§°
     */
    private String categoryName;
    /**
     * å“ç‰Œåç§°
     */
    private String brandName;
    /**
     * SKUå›¾ç‰‡åˆ—è¡¨
     */
    private List<SkuImageVo> images;
    /**
     * é”€å”®å±žæ€§åˆ—è¡¨
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

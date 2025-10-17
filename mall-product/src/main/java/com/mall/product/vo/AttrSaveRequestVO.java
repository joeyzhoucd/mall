package com.mall.product.vo;

import lombok.Data;

@Data
public class AttrSaveRequestVO {
    private Long attrId;           // å…è®¸ä¸º nullï¼Œè¡¨ç¤ºæ–°å¢ž
    private String attrName;
    private Integer searchType;
    private Integer valueType;
    private String valueSelect;
    private String attrGroupId;
    private String attrGroupName;  // è¿™é‡Œåªæ˜¯å‰ç«¯å±•ç¤ºï¼Œä¸å…¥è¡¨
    private String icon;
    private Integer showDesc;
    private String categoryId;
}

package com.mall.product.vo;

import lombok.Data;

@Data
public class AttrSaveRequestVO {
    private Long attrId;           // null for new attributes
    private String attrName;
    private Integer searchType;
    private Integer valueType;
    private String valueSelect;
    private String attrGroupId;
    private String attrGroupName;  // Only for display purposes
    private String icon;
    private Integer showDesc;
    private String categoryId;
}
package com.joeyzhoucd.product.vo;

import lombok.Data;

@Data
public class AttrSaveRequestVO {
    private Long attrId;           // 允许为 null，表示新增
    private String attrName;
    private Integer searchType;
    private Integer valueType;
    private String valueSelect;
    private String attrGroupId;
    private String attrGroupName;  // 这里只是前端展示，不入表
    private String icon;
    private Integer showDesc;
    private String categoryId;
}

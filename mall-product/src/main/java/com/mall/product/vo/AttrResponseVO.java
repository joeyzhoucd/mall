package com.mall.product.vo;

import lombok.Data;
import lombok.ToString;

@Data
@ToString
public class AttrResponseVO {
    private Long attrId;
    
    private String attrName;
    
    private Integer searchType;
    
    private String icon;
    
    private String valueSelect;
    
    private Integer attrType;
    
    private Long enable;
    
    private Long categoryId;
    
    private Integer showDesc;

    private String categoryName;

    private Long attrGroupId;

    private String attrGroupName;
}

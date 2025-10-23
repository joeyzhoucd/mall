package com.mall.product.vo;

import com.mall.product.entity.AttrEntity;
import lombok.Data;

@Data
public class AttrAttrgroupRelationVO {
    private Long id;
    
    private Long attrId;
    
    private String attrName;
    
    private Long attrGroupId;
    
    private Integer attrSort;

    private AttrEntity attr;
}

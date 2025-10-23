package com.mall.product.vo;

import com.mall.product.entity.AttrEntity;
import lombok.Data;

import java.util.List;

@Data
public class AttrGroupWithAttrVO {

    private Long attrGroupId;
    
    private String attrGroupName;
    
    private Integer sort;
    
    private String descript;
    
    private String icon;
    
    private Long categoryId;

    private List<AttrEntity> attrs;
}

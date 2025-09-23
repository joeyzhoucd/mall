package com.joeyzhoucd.product.vo;

import com.joeyzhoucd.product.entity.AttrEntity;
import lombok.Data;

@Data
public class AttrAttrgroupRelationVO {
    private Long id;
    /**
     * 属性id
     */
    private Long attrId;
    /**
     * 属性名称（用于前端直接展示）
     */
    private String attrName;
    /**
     * 属性分组id
     */
    private Long attrGroupId;
    /**
     * 属性组内排序
     */
    private Integer attrSort;

    private AttrEntity attr;
}

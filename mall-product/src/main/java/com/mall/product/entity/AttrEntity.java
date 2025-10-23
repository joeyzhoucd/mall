package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("pms_attr")
public class AttrEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long attrId;
    
    private String attrName;
    
    private Integer searchType;
    
    private String icon;
    
    private String valueSelect;
    
    private Integer attrType;
    
    private Long enable;
    
    private Long categoryId;
    
    private Integer showDesc;

}

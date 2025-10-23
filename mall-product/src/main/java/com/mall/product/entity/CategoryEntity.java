package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.List;


@Data
@TableName("pms_category")
public class CategoryEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long catId;
    
    private String name;
    
    private Long parentCid;
    
    private Integer catLevel;
    
    private Integer showStatus;
    
    private Integer sort = 0;
    
    private String icon;
    
    private String productUnit;
    
    private Integer productCount;

    @TableField(exist = false)
    private List<CategoryEntity> children;

    @TableLogic   // Logical deletion field
    private Integer deleted;


}
package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("pms_category_brand_relation")
public class CategoryBrandRelationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long id;
    
    private Long brandId;
    
    private Long categoryId;
    
    private String brandName;

    
    private String categoryName;

}

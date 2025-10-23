package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("pms_product_attr_value")
public class ProductAttrValueEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long id;
    
    private Long spuId;
    
    private Long attrId;
    
    private String attrName;
    
    private String attrValue;
    
    private Integer attrSort;
    
    private Integer quickShow;

}

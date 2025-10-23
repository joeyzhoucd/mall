package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("pms_sku_info")
public class SkuInfoEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long skuId;
    
    private Long spuId;
    
    private String skuName;
    
    private String skuDesc;
    
    private Long categoryId;
    
    private Long brandId;
    
    private String skuDefaultImg;
    
    private String skuTitle;
    
    private String skuSubtitle;
    
    private BigDecimal price;
    
    private Long saleCount;

}

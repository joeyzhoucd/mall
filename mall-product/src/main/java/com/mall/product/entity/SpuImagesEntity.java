package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("pms_spu_images")
public class SpuImagesEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long id;
    
    private Long spuId;
    
    private String imgName;
    
    private String imgUrl;
    
    private Integer imgSort;
    
    private Integer defaultImg;

}

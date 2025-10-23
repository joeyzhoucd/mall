package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("pms_spu_info_desc")
public class SpuInfoDescEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId(type = IdType.INPUT)
    private Long spuId;
    
    private String decript;

}

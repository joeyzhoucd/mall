package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.validator.group.AddGroup;
import com.mall.common.validator.group.UpdateGroup;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import java.io.Serializable;


@Data
@TableName("pms_brand")
public class BrandEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    @NotBlank(message = "Brand ID cannot be empty", groups = UpdateGroup.class)
    @Null(message = "Brand ID must be null", groups = AddGroup.class)
    private Long brandId;
    
    @NotBlank(message = "Brand name cannot be empty")
    private String name;
    
    private String logo;
    
    private String descript;
    
    private Integer showStatus;
    
    private String firstLetter;
    
    private Integer sort;

}
package com.joeyzhoucd.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 属性&属性分组关联
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_category_brand_relation")
public class CategoryBrandRelationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * 属性id
     */
    private Long brandId;
    /**
     * 属性分组id
     */
    private Long categoryId;
    /**
     * 品牌名称(冗余存储，便于查询)
     */
    private String brandName;

    /**
     * 分类名称(冗余存储，便于查询)
     */
    private String categoryName;

}

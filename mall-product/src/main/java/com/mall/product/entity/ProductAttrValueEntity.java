package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * spuå±žæ€§å€¼
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_product_attr_value")
public class ProductAttrValueEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * å•†å“id
     */
    private Long spuId;
    /**
     * å±žæ€§id
     */
    private Long attrId;
    /**
     * å±žæ€§å
     */
    private String attrName;
    /**
     * å±žæ€§å€¼
     */
    private String attrValue;
    /**
     * é¡ºåº
     */
    private Integer attrSort;
    /**
     * å¿«é€Ÿå±•ç¤ºã€æ˜¯å¦å±•ç¤ºåœ¨ä»‹ç»ä¸Šï¼›0-å¦ 1-æ˜¯ã€‘
     */
    private Integer quickShow;

}

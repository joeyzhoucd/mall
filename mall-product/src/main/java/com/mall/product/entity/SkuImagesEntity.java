package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * skuå›¾ç‰‡
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_sku_images")
public class SkuImagesEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * sku_id
     */
    private Long skuId;
    /**
     * å›¾ç‰‡åœ°å€
     */
    private String imgUrl;
    /**
     * æŽ’åº
     */
    private Integer imgSort;
    /**
     * é»˜è®¤å›¾[0 - ä¸æ˜¯é»˜è®¤å›¾ï¼Œ1 - æ˜¯é»˜è®¤å›¾]
     */
    private Integer defaultImg;

}

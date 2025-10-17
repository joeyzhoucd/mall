package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * å•†å“åº“å­˜
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("wms_ware_sku")
public class WareSkuEntity implements Serializable {
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
     * ä»“åº“id
     */
    private Long wareId;
    /**
     * åº“å­˜æ•°
     */
    private Integer stock;
    /**
     * sku_name
     */
    private String skuName;
    /**
     * é”å®šåº“å­˜
     */
    private Integer stockLocked;
}

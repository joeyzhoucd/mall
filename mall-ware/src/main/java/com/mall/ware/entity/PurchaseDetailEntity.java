package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * é‡‡è´­éœ€æ±‚/é‡‡è´­é¡¹
 */
@Data
@TableName("wms_purchase_detail")
public class PurchaseDetailEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * 
	 */
	@TableId
	private Long id;
	/**
	 * é‡‡è´­å•id
	 */
	private Long purchaseId;
	/**
	 * é‡‡è´­å•†å“id
	 */
	private Long skuId;
	/**
	 * é‡‡è´­æ•°é‡
	 */
	private Integer skuNum;
	/**
	 * é‡‡è´­é‡‘é¢
	 */
	private BigDecimal skuPrice;
	/**
	 * ä»“åº“id
	 */
	private Long wareId;
	/**
	 * çŠ¶æ€[0æ–°å»ºï¼Œ1å·²åˆ†é…ï¼Œ2æ­£åœ¨é‡‡è´­ï¼Œ3å·²å®Œæˆï¼Œ4é‡‡è´­å¤±è´¥]
	 */
	private Integer status;

}

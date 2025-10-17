package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ç§’æ€æ´»åŠ¨å•†å“å…³è”
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_seckill_sku_relation")
public class SeckillSkuRelationEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * æ´»åŠ¨id
	 */
	private Long promotionId;
	/**
	 * æ´»åŠ¨åœºæ¬¡id
	 */
	private Long promotionSessionId;
	/**
	 * å•†å“id
	 */
	private Long skuId;
	/**
	 * ç§’æ€ä»·æ ¼
	 */
	private BigDecimal seckillPrice;
	/**
	 * ç§’æ€æ€»é‡
	 */
	private BigDecimal seckillCount;
	/**
	 * æ¯äººé™è´­æ•°é‡
	 */
	private BigDecimal seckillLimit;
	/**
	 * æŽ’åº
	 */
	private Integer seckillSort;

}

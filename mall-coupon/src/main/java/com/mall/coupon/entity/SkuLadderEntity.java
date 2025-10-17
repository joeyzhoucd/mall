package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * å•†å“é˜¶æ¢¯ä»·æ ¼
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_sku_ladder")
public class SkuLadderEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * spu_id
	 */
	private Long skuId;
	/**
	 * æ»¡å‡ ä»¶
	 */
	private Integer fullCount;
	/**
	 * æ‰“å‡ æŠ˜
	 */
	private BigDecimal discount;
	/**
	 * æŠ˜åŽä»·
	 */
	private BigDecimal price;
	/**
	 * æ˜¯å¦å åŠ å…¶ä»–ä¼˜æƒ [0-ä¸å¯å åŠ ï¼Œ1-å¯å åŠ ]
	 */
	private Integer addOther;

}

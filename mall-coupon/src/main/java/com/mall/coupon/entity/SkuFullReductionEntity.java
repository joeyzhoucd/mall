package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * å•†å“æ»¡å‡ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_sku_full_reduction")
public class SkuFullReductionEntity implements Serializable {
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
	 * æ»¡å¤šå°‘
	 */
	private BigDecimal fullPrice;
	/**
	 * å‡å¤šå°‘
	 */
	private BigDecimal reducePrice;
	/**
	 * æ˜¯å¦å‚ä¸Žå…¶ä»–ä¼˜æƒ 
	 */
	private Integer addOther;

}

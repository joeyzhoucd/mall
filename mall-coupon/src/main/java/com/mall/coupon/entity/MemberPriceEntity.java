package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * å•†å“ä¼šå‘˜ä»·æ ¼
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_member_price")
public class MemberPriceEntity implements Serializable {
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
	 * ä¼šå‘˜ç­‰çº§id
	 */
	private Long memberLevelId;
	/**
	 * ä¼šå‘˜ç­‰çº§å
	 */
	private String memberLevelName;
	/**
	 * ä¼šå‘˜å¯¹åº”ä»·æ ¼
	 */
	private BigDecimal memberPrice;
	/**
	 * å¯å¦å åŠ å…¶ä»–ä¼˜æƒ [0-ä¸å¯å åŠ ä¼˜æƒ ï¼Œ1-å¯å åŠ ]
	 */
	private Integer addOther;

}

package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * å•†å“spuç§¯åˆ†è®¾ç½®
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_spu_bounds")
public class SpuBoundsEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * 
	 */
	private Long spuId;
	/**
	 * æˆé•¿ç§¯åˆ†
	 */
	private BigDecimal growBounds;
	/**
	 * è´­ç‰©ç§¯åˆ†
	 */
	private BigDecimal buyBounds;
	/**
	 * ä¼˜æƒ ç”Ÿæ•ˆæƒ…å†µ[1111ï¼ˆå››ä¸ªçŠ¶æ€ä½ï¼Œä»Žå³åˆ°å·¦ï¼‰;0 - æ— ä¼˜æƒ ï¼Œæˆé•¿ç§¯åˆ†æ˜¯å¦èµ é€;1 - æ— ä¼˜æƒ ï¼Œè´­ç‰©ç§¯åˆ†æ˜¯å¦èµ é€;2 - æœ‰ä¼˜æƒ ï¼Œæˆé•¿ç§¯åˆ†æ˜¯å¦èµ é€;3 - æœ‰ä¼˜æƒ ï¼Œè´­ç‰©ç§¯åˆ†æ˜¯å¦èµ é€ã€çŠ¶æ€ä½0ï¼šä¸èµ é€ï¼Œ1ï¼šèµ é€ã€‘]
	 */
	private Integer work;

}

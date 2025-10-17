package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * æ”¯ä»˜ä¿¡æ¯è¡¨
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_payment_info")
public class PaymentInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * è®¢å•å·ï¼ˆå¯¹å¤–ä¸šåŠ¡å·ï¼‰
	 */
	private String orderSn;
	/**
	 * è®¢å•id
	 */
	private Long orderId;
	/**
	 * æ”¯ä»˜å®äº¤æ˜“æµæ°´å·
	 */
	private String alipayTradeNo;
	/**
	 * æ”¯ä»˜æ€»é‡‘é¢
	 */
	private BigDecimal totalAmount;
	/**
	 * äº¤æ˜“å†…å®¹
	 */
	private String subject;
	/**
	 * æ”¯ä»˜çŠ¶æ€
	 */
	private String paymentStatus;
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;
	/**
	 * ç¡®è®¤æ—¶é—´
	 */
	private Date confirmTime;
	/**
	 * å›žè°ƒå†…å®¹
	 */
	private String callbackContent;
	/**
	 * å›žè°ƒæ—¶é—´
	 */
	private Date callbackTime;

}

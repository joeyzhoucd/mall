package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * é€€æ¬¾ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_refund_info")
public class RefundInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * é€€æ¬¾çš„è®¢å•
	 */
	private Long orderReturnId;
	/**
	 * é€€æ¬¾é‡‘é¢
	 */
	private BigDecimal refund;
	/**
	 * é€€æ¬¾äº¤æ˜“æµæ°´å·
	 */
	private String refundSn;
	/**
	 * é€€æ¬¾çŠ¶æ€
	 */
	private Integer refundStatus;
	/**
	 * é€€æ¬¾æ¸ é“[1-æ”¯ä»˜å®ï¼Œ2-å¾®ä¿¡ï¼Œ3-é“¶è”ï¼Œ4-æ±‡æ¬¾]
	 */
	private Integer refundChannel;
	/**
	 * 
	 */
	private String refundContent;

}

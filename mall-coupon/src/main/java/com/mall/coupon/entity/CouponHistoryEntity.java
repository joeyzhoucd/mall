package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ä¼˜æƒ åˆ¸é¢†å–åŽ†å²è®°å½•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_coupon_history")
public class CouponHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¼˜æƒ åˆ¸id
	 */
	private Long couponId;
	/**
	 * ä¼šå‘˜id
	 */
	private Long memberId;
	/**
	 * ä¼šå‘˜åå­—
	 */
	private String memberNickName;
	/**
	 * èŽ·å–æ–¹å¼[0->åŽå°èµ é€ï¼›1->ä¸»åŠ¨é¢†å–]
	 */
	private Integer getType;
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;
	/**
	 * ä½¿ç”¨çŠ¶æ€[0->æœªä½¿ç”¨ï¼›1->å·²ä½¿ç”¨ï¼›2->å·²è¿‡æœŸ]
	 */
	private Integer useType;
	/**
	 * ä½¿ç”¨æ—¶é—´
	 */
	private Date useTime;
	/**
	 * è®¢å•id
	 */
	private Long orderId;
	/**
	 * è®¢å•å·
	 */
	private Long orderSn;

}

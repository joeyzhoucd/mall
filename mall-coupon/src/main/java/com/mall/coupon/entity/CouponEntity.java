package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * ä¼˜æƒ åˆ¸ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_coupon")
public class CouponEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¼˜æƒ å·ç±»åž‹[0->å…¨åœºèµ åˆ¸ï¼›1->ä¼šå‘˜èµ åˆ¸ï¼›2->è´­ç‰©èµ åˆ¸ï¼›3->æ³¨å†Œèµ åˆ¸]
	 */
	private Integer couponType;
	/**
	 * ä¼˜æƒ åˆ¸å›¾ç‰‡
	 */
	private String couponImg;
	/**
	 * ä¼˜æƒ å·åå­—
	 */
	private String couponName;
	/**
	 * æ•°é‡
	 */
	private Integer num;
	/**
	 * é‡‘é¢
	 */
	private BigDecimal amount;
	/**
	 * æ¯äººé™é¢†å¼ æ•°
	 */
	private Integer perLimit;
	/**
	 * ä½¿ç”¨é—¨æ§›
	 */
	private BigDecimal minPoint;
	/**
	 * å¼€å§‹æ—¶é—´
	 */
	private Date startTime;
	/**
	 * ç»“æŸæ—¶é—´
	 */
	private Date endTime;
	/**
	 * ä½¿ç”¨ç±»åž‹[0->å…¨åœºé€šç”¨ï¼›1->æŒ‡å®šåˆ†ç±»ï¼›2->æŒ‡å®šå•†å“]
	 */
	private Integer useType;
	/**
	 * å¤‡æ³¨
	 */
	private String note;
	/**
	 * å‘è¡Œæ•°é‡
	 */
	private Integer publishCount;
	/**
	 * å·²ä½¿ç”¨æ•°é‡
	 */
	private Integer useCount;
	/**
	 * é¢†å–æ•°é‡
	 */
	private Integer receiveCount;
	/**
	 * å¯ä»¥é¢†å–çš„å¼€å§‹æ—¥æœŸ
	 */
	private Date enableStartTime;
	/**
	 * å¯ä»¥é¢†å–çš„ç»“æŸæ—¥æœŸ
	 */
	private Date enableEndTime;
	/**
	 * ä¼˜æƒ ç 
	 */
	private String code;
	/**
	 * å¯ä»¥é¢†å–çš„ä¼šå‘˜ç­‰çº§[0->ä¸é™ç­‰çº§ï¼Œå…¶ä»–-å¯¹åº”ç­‰çº§]
	 */
	private Integer memberLevel;
	/**
	 * å‘å¸ƒçŠ¶æ€[0-æœªå‘å¸ƒï¼Œ1-å·²å‘å¸ƒ]
	 */
	private Integer publish;

}

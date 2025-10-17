package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * é¦–é¡µè½®æ’­å¹¿å‘Š
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_home_adv")
public class HomeAdvEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * åå­—
	 */
	private String name;
	/**
	 * å›¾ç‰‡åœ°å€
	 */
	private String pic;
	/**
	 * å¼€å§‹æ—¶é—´
	 */
	private Date startTime;
	/**
	 * ç»“æŸæ—¶é—´
	 */
	private Date endTime;
	/**
	 * çŠ¶æ€
	 */
	private Integer status;
	/**
	 * ç‚¹å‡»æ•°
	 */
	private Integer clickCount;
	/**
	 * å¹¿å‘Šè¯¦æƒ…è¿žæŽ¥åœ°å€
	 */
	private String url;
	/**
	 * å¤‡æ³¨
	 */
	private String note;
	/**
	 * æŽ’åº
	 */
	private Integer sort;
	/**
	 * å‘å¸ƒè€…
	 */
	private Long publisherId;
	/**
	 * å®¡æ ¸è€…
	 */
	private Long authId;

}

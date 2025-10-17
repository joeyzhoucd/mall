package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ç§’æ€æ´»åŠ¨
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_seckill_promotion")
public class SeckillPromotionEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * æ´»åŠ¨æ ‡é¢˜
	 */
	private String title;
	/**
	 * å¼€å§‹æ—¥æœŸ
	 */
	private Date startTime;
	/**
	 * ç»“æŸæ—¥æœŸ
	 */
	private Date endTime;
	/**
	 * ä¸Šä¸‹çº¿çŠ¶æ€
	 */
	private Integer status;
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;
	/**
	 * åˆ›å»ºäºº
	 */
	private Long userId;

}

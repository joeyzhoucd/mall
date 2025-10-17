package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ç§’æ€æ´»åŠ¨åœºæ¬¡
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_seckill_session")
public class SeckillSessionEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * åœºæ¬¡åç§°
	 */
	private String name;
	/**
	 * æ¯æ—¥å¼€å§‹æ—¶é—´
	 */
	private Date startTime;
	/**
	 * æ¯æ—¥ç»“æŸæ—¶é—´
	 */
	private Date endTime;
	/**
	 * å¯ç”¨çŠ¶æ€
	 */
	private Integer status;
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;

}

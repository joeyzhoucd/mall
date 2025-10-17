package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ç§’æ€å•†å“é€šçŸ¥è®¢é˜…
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_seckill_sku_notice")
public class SeckillSkuNoticeEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * member_id
	 */
	private Long memberId;
	/**
	 * sku_id
	 */
	private Long skuId;
	/**
	 * æ´»åŠ¨åœºæ¬¡id
	 */
	private Long sessionId;
	/**
	 * è®¢é˜…æ—¶é—´
	 */
	private Date subcribeTime;
	/**
	 * å‘é€æ—¶é—´
	 */
	private Date sendTime;
	/**
	 * é€šçŸ¥æ–¹å¼[0-çŸ­ä¿¡ï¼Œ1-é‚®ä»¶]
	 */
	private Integer noticeType;

}

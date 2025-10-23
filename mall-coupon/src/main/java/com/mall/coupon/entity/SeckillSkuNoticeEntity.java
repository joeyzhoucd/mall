package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("sms_seckill_sku_notice")
public class SeckillSkuNoticeEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long memberId;
	
	private Long skuId;
	
	private Long sessionId;
	
	private Date subcribeTime;
	
	private Date sendTime;
	
	private Integer noticeType;

}

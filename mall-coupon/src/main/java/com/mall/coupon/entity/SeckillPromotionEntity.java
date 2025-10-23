package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("sms_seckill_promotion")
public class SeckillPromotionEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private String title;
	
	private Date startTime;
	
	private Date endTime;
	
	private Integer status;
	
	private Date createTime;
	
	private Long userId;

}

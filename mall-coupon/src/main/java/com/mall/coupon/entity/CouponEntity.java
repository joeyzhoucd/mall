package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("sms_coupon")
public class CouponEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Integer couponType;
	
	private String couponImg;
	
	private String couponName;
	
	private Integer num;
	
	private BigDecimal amount;
	
	private Integer perLimit;
	
	private BigDecimal minPoint;
	
	private Date startTime;
	
	private Date endTime;
	
	private Integer useType;
	
	private String note;
	
	private Integer publishCount;
	
	private Integer useCount;
	
	private Integer receiveCount;
	
	private Date enableStartTime;
	
	private Date enableEndTime;
	
	private String code;
	
	private Integer memberLevel;
	
	private Integer publish;

}

package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("sms_member_price")
public class MemberPriceEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long skuId;
	
	private Long memberLevelId;
	
	private String memberLevelName;
	
	private BigDecimal memberPrice;
	
	private Integer addOther;

}

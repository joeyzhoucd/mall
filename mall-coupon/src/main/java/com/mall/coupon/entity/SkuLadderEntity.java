package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("sms_sku_ladder")
public class SkuLadderEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long skuId;
	
	private Integer fullCount;
	
	private BigDecimal discount;
	
	private BigDecimal price;
	
	private Integer addOther;

}

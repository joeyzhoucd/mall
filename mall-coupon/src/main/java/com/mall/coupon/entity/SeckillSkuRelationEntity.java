package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("sms_seckill_sku_relation")
public class SeckillSkuRelationEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long promotionId;
	
	private Long promotionSessionId;
	
	private Long skuId;
	
	private BigDecimal seckillPrice;
	
	private BigDecimal seckillCount;
	
	private BigDecimal seckillLimit;
	
	private Integer seckillSort;

	private Integer soldCount;

}

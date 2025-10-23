package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("sms_sku_full_reduction")
public class SkuFullReductionEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long skuId;
	
	private BigDecimal fullPrice;
	
	private BigDecimal reducePrice;
	
	private Integer addOther;

}

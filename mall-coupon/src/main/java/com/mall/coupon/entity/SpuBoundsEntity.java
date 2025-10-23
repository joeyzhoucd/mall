package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("sms_spu_bounds")
public class SpuBoundsEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long spuId;
	
	private BigDecimal growBounds;
	
	private BigDecimal buyBounds;
	
	private Integer work;

}

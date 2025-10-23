package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("wms_purchase_detail")
public class PurchaseDetailEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long purchaseId;
	
	private Long skuId;
	
	private Integer skuNum;
	
	private BigDecimal skuPrice;
	
	private Long wareId;
	
	private Integer status;

}

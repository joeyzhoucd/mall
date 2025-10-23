package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("oms_payment_info")
public class PaymentInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private String orderSn;
	
	private Long orderId;
	
	private String alipayTradeNo;
	
	private BigDecimal totalAmount;
	
	private String subject;
	
	private String paymentStatus;
	
	private Date createTime;
	
	private Date confirmTime;
	
	private String callbackContent;
	
	private Date callbackTime;

}

package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("oms_refund_info")
public class RefundInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long orderReturnId;

	private String orderSn;
	
	private BigDecimal refund;
	
	private String refundSn;

	private String paymentChannel;

	private String tradeNo;

	private String refundTradeNo;

	private String currency;
	
	private Integer refundStatus;
	
	private Integer refundChannel;
	
	private String refundContent;

}

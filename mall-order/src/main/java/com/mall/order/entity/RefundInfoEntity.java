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
	
	private BigDecimal refund;
	
	private String refundSn;
	
	private Integer refundStatus;
	
	private Integer refundChannel;
	
	private String refundContent;

}

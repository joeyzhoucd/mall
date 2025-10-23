package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;


@Data
@TableName("oms_order")
public class OrderEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long memberId;
	
	private String orderSn;
	
	private Long couponId;
	
	private Date createTime;
	
	private String memberUsername;
	
	private BigDecimal totalAmount;
	
	private BigDecimal payAmount;
	
	private BigDecimal freightAmount;
	
	private BigDecimal promotionAmount;
	
	private BigDecimal integrationAmount;
	
	private BigDecimal couponAmount;
	
	private BigDecimal discountAmount;
	
	private Integer payType;
	
	private Integer sourceType;
	
	private Integer status;
	
	private String deliveryCompany;
	
	private String deliverySn;
	
	private Integer autoConfirmDay;
	
	private Integer integration;
	
	private Integer growth;
	
	private Integer billType;
	
	private String billHeader;
	
	private String billContent;
	
	private String billReceiverPhone;
	
	private String billReceiverEmail;
	
	private String receiverName;
	
	private String receiverPhone;
	
	private String receiverPostCode;
	
	private String receiverProvince;
	
	private String receiverCity;
	
	private String receiverRegion;
	
	private String receiverDetailAddress;
	
	private String note;
	
	private Integer confirmStatus;
	
	private Integer deleteStatus;
	
	private Integer useIntegration;
	
	private Date paymentTime;
	
	private Date deliveryTime;
	
	private Date receiveTime;
	
	private Date commentTime;
	
	private Date modifyTime;

}

package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("wms_ware_order_task")
public class WareOrderTaskEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long orderId;
	
	private String orderSn;
	
	private String consignee;
	
	private String consigneeTel;
	
	private String deliveryAddress;
	
	private String orderComment;
	
	private Integer paymentWay;
	
	private Integer taskStatus;
	
	private String orderBody;
	
	private String trackingNo;
	
	private Date createTime;
	
	private Long wareId;
	
	private String taskComment;

}

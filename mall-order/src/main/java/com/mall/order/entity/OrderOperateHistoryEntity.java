package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("oms_order_operate_history")
public class OrderOperateHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long orderId;
	
	private String operateMan;
	
	private Date createTime;
	
	private Integer orderStatus;
	
	private String note;

}

package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("oms_order_return_reason")
public class OrderReturnReasonEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private String name;
	
	private Integer sort;
	
	private Integer status;
	
	private Date createTime;

}

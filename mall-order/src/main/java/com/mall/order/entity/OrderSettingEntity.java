package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("oms_order_setting")
public class OrderSettingEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Integer flashOrderOvertime;
	
	private Integer normalOrderOvertime;
	
	private Integer confirmOvertime;
	
	private Integer finishOvertime;
	
	private Integer commentOvertime;
	
	private Integer memberLevel;

}

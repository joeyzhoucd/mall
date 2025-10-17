package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * é€€è´§åŽŸå› 
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order_return_reason")
public class OrderReturnReasonEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * é€€è´§åŽŸå› å
	 */
	private String name;
	/**
	 * æŽ’åº
	 */
	private Integer sort;
	/**
	 * å¯ç”¨çŠ¶æ€
	 */
	private Integer status;
	/**
	 * create_time
	 */
	private Date createTime;

}

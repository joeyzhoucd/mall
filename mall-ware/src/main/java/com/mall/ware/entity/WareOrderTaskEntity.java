package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * åº“å­˜å·¥ä½œå•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:27:58
 */
@Data
@TableName("wms_ware_order_task")
public class WareOrderTaskEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * order_id
	 */
	private Long orderId;
	/**
	 * order_sn
	 */
	private String orderSn;
	/**
	 * æ”¶è´§äºº
	 */
	private String consignee;
	/**
	 * æ”¶è´§äººç”µè¯
	 */
	private String consigneeTel;
	/**
	 * é…é€åœ°å€
	 */
	private String deliveryAddress;
	/**
	 * è®¢å•å¤‡æ³¨
	 */
	private String orderComment;
	/**
	 * ä»˜æ¬¾æ–¹å¼ã€ 1:åœ¨çº¿ä»˜æ¬¾ 2:è´§åˆ°ä»˜æ¬¾ã€‘
	 */
	private Integer paymentWay;
	/**
	 * ä»»åŠ¡çŠ¶æ€
	 */
	private Integer taskStatus;
	/**
	 * è®¢å•æè¿°
	 */
	private String orderBody;
	/**
	 * ç‰©æµå•å·
	 */
	private String trackingNo;
	/**
	 * create_time
	 */
	private Date createTime;
	/**
	 * ä»“åº“id
	 */
	private Long wareId;
	/**
	 * å·¥ä½œå•å¤‡æ³¨
	 */
	private String taskComment;

}

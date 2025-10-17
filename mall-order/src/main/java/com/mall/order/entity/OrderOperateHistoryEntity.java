package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * è®¢å•æ“ä½œåŽ†å²è®°å½•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order_operate_history")
public class OrderOperateHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * è®¢å•id
	 */
	private Long orderId;
	/**
	 * æ“ä½œäºº[ç”¨æˆ·ï¼›ç³»ç»Ÿï¼›åŽå°ç®¡ç†å‘˜]
	 */
	private String operateMan;
	/**
	 * æ“ä½œæ—¶é—´
	 */
	private Date createTime;
	/**
	 * è®¢å•çŠ¶æ€ã€0->å¾…ä»˜æ¬¾ï¼›1->å¾…å‘è´§ï¼›2->å·²å‘è´§ï¼›3->å·²å®Œæˆï¼›4->å·²å…³é—­ï¼›5->æ— æ•ˆè®¢å•ã€‘
	 */
	private Integer orderStatus;
	/**
	 * å¤‡æ³¨
	 */
	private String note;

}

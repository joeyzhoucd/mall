package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * è®¢å•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order")
public class OrderEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * member_id
	 */
	private Long memberId;
	/**
	 * è®¢å•å·
	 */
	private String orderSn;
	/**
	 * ä½¿ç”¨çš„ä¼˜æƒ åˆ¸
	 */
	private Long couponId;
	/**
	 * create_time
	 */
	private Date createTime;
	/**
	 * ç”¨æˆ·å
	 */
	private String memberUsername;
	/**
	 * è®¢å•æ€»é¢
	 */
	private BigDecimal totalAmount;
	/**
	 * åº”ä»˜æ€»é¢
	 */
	private BigDecimal payAmount;
	/**
	 * è¿è´¹é‡‘é¢
	 */
	private BigDecimal freightAmount;
	/**
	 * ä¿ƒé”€ä¼˜åŒ–é‡‘é¢ï¼ˆä¿ƒé”€ä»·ã€æ»¡å‡ã€é˜¶æ¢¯ä»·ï¼‰
	 */
	private BigDecimal promotionAmount;
	/**
	 * ç§¯åˆ†æŠµæ‰£é‡‘é¢
	 */
	private BigDecimal integrationAmount;
	/**
	 * ä¼˜æƒ åˆ¸æŠµæ‰£é‡‘é¢
	 */
	private BigDecimal couponAmount;
	/**
	 * åŽå°è°ƒæ•´è®¢å•ä½¿ç”¨çš„æŠ˜æ‰£é‡‘é¢
	 */
	private BigDecimal discountAmount;
	/**
	 * æ”¯ä»˜æ–¹å¼ã€1->æ”¯ä»˜å®ï¼›2->å¾®ä¿¡ï¼›3->é“¶è”ï¼› 4->è´§åˆ°ä»˜æ¬¾ï¼›ã€‘
	 */
	private Integer payType;
	/**
	 * è®¢å•æ¥æº[0->PCè®¢å•ï¼›1->appè®¢å•]
	 */
	private Integer sourceType;
	/**
	 * è®¢å•çŠ¶æ€ã€0->å¾…ä»˜æ¬¾ï¼›1->å¾…å‘è´§ï¼›2->å·²å‘è´§ï¼›3->å·²å®Œæˆï¼›4->å·²å…³é—­ï¼›5->æ— æ•ˆè®¢å•ã€‘
	 */
	private Integer status;
	/**
	 * ç‰©æµå…¬å¸(é…é€æ–¹å¼)
	 */
	private String deliveryCompany;
	/**
	 * ç‰©æµå•å·
	 */
	private String deliverySn;
	/**
	 * è‡ªåŠ¨ç¡®è®¤æ—¶é—´ï¼ˆå¤©ï¼‰
	 */
	private Integer autoConfirmDay;
	/**
	 * å¯ä»¥èŽ·å¾—çš„ç§¯åˆ†
	 */
	private Integer integration;
	/**
	 * å¯ä»¥èŽ·å¾—çš„æˆé•¿å€¼
	 */
	private Integer growth;
	/**
	 * å‘ç¥¨ç±»åž‹[0->ä¸å¼€å‘ç¥¨ï¼›1->ç”µå­å‘ç¥¨ï¼›2->çº¸è´¨å‘ç¥¨]
	 */
	private Integer billType;
	/**
	 * å‘ç¥¨æŠ¬å¤´
	 */
	private String billHeader;
	/**
	 * å‘ç¥¨å†…å®¹
	 */
	private String billContent;
	/**
	 * æ”¶ç¥¨äººç”µè¯
	 */
	private String billReceiverPhone;
	/**
	 * æ”¶ç¥¨äººé‚®ç®±
	 */
	private String billReceiverEmail;
	/**
	 * æ”¶è´§äººå§“å
	 */
	private String receiverName;
	/**
	 * æ”¶è´§äººç”µè¯
	 */
	private String receiverPhone;
	/**
	 * æ”¶è´§äººé‚®ç¼–
	 */
	private String receiverPostCode;
	/**
	 * çœä»½/ç›´è¾–å¸‚
	 */
	private String receiverProvince;
	/**
	 * åŸŽå¸‚
	 */
	private String receiverCity;
	/**
	 * åŒº
	 */
	private String receiverRegion;
	/**
	 * è¯¦ç»†åœ°å€
	 */
	private String receiverDetailAddress;
	/**
	 * è®¢å•å¤‡æ³¨
	 */
	private String note;
	/**
	 * ç¡®è®¤æ”¶è´§çŠ¶æ€[0->æœªç¡®è®¤ï¼›1->å·²ç¡®è®¤]
	 */
	private Integer confirmStatus;
	/**
	 * åˆ é™¤çŠ¶æ€ã€0->æœªåˆ é™¤ï¼›1->å·²åˆ é™¤ã€‘
	 */
	private Integer deleteStatus;
	/**
	 * ä¸‹å•æ—¶ä½¿ç”¨çš„ç§¯åˆ†
	 */
	private Integer useIntegration;
	/**
	 * æ”¯ä»˜æ—¶é—´
	 */
	private Date paymentTime;
	/**
	 * å‘è´§æ—¶é—´
	 */
	private Date deliveryTime;
	/**
	 * ç¡®è®¤æ”¶è´§æ—¶é—´
	 */
	private Date receiveTime;
	/**
	 * è¯„ä»·æ—¶é—´
	 */
	private Date commentTime;
	/**
	 * ä¿®æ”¹æ—¶é—´
	 */
	private Date modifyTime;

}

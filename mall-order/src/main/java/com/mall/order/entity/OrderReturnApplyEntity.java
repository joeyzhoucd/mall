package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * è®¢å•é€€è´§ç”³è¯·
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order_return_apply")
public class OrderReturnApplyEntity implements Serializable {
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
	 * é€€è´§å•†å“id
	 */
	private Long skuId;
	/**
	 * è®¢å•ç¼–å·
	 */
	private String orderSn;
	/**
	 * ç”³è¯·æ—¶é—´
	 */
	private Date createTime;
	/**
	 * ä¼šå‘˜ç”¨æˆ·å
	 */
	private String memberUsername;
	/**
	 * é€€æ¬¾é‡‘é¢
	 */
	private BigDecimal returnAmount;
	/**
	 * é€€è´§äººå§“å
	 */
	private String returnName;
	/**
	 * é€€è´§äººç”µè¯
	 */
	private String returnPhone;
	/**
	 * ç”³è¯·çŠ¶æ€[0->å¾…å¤„ç†ï¼›1->é€€è´§ä¸­ï¼›2->å·²å®Œæˆï¼›3->å·²æ‹’ç»]
	 */
	private Integer status;
	/**
	 * å¤„ç†æ—¶é—´
	 */
	private Date handleTime;
	/**
	 * å•†å“å›¾ç‰‡
	 */
	private String skuImg;
	/**
	 * å•†å“åç§°
	 */
	private String skuName;
	/**
	 * å•†å“å“ç‰Œ
	 */
	private String skuBrand;
	/**
	 * å•†å“é”€å”®å±žæ€§(JSON)
	 */
	private String skuAttrsVals;
	/**
	 * é€€è´§æ•°é‡
	 */
	private Integer skuCount;
	/**
	 * å•†å“å•ä»·
	 */
	private BigDecimal skuPrice;
	/**
	 * å•†å“å®žé™…æ”¯ä»˜å•ä»·
	 */
	private BigDecimal skuRealPrice;
	/**
	 * åŽŸå› 
	 */
	private String reason;
	/**
	 * æè¿°
	 */
	private String description;
	/**
	 * å‡­è¯å›¾ç‰‡ï¼Œä»¥é€—å·éš”å¼€
	 */
	private String descPics;
	/**
	 * å¤„ç†å¤‡æ³¨
	 */
	private String handleNote;
	/**
	 * å¤„ç†äººå‘˜
	 */
	private String handleMan;
	/**
	 * æ”¶è´§äºº
	 */
	private String receiveMan;
	/**
	 * æ”¶è´§æ—¶é—´
	 */
	private Date receiveTime;
	/**
	 * æ”¶è´§å¤‡æ³¨
	 */
	private String receiveNote;
	/**
	 * æ”¶è´§ç”µè¯
	 */
	private String receivePhone;
	/**
	 * å…¬å¸æ”¶è´§åœ°å€
	 */
	private String companyAddress;

}

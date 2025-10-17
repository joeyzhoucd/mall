package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * è®¢å•é…ç½®ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order_setting")
public class OrderSettingEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ç§’æ€è®¢å•è¶…æ—¶å…³é—­æ—¶é—´(åˆ†)
	 */
	private Integer flashOrderOvertime;
	/**
	 * æ­£å¸¸è®¢å•è¶…æ—¶æ—¶é—´(åˆ†)
	 */
	private Integer normalOrderOvertime;
	/**
	 * å‘è´§åŽè‡ªåŠ¨ç¡®è®¤æ”¶è´§æ—¶é—´ï¼ˆå¤©ï¼‰
	 */
	private Integer confirmOvertime;
	/**
	 * è‡ªåŠ¨å®Œæˆäº¤æ˜“æ—¶é—´ï¼Œä¸èƒ½ç”³è¯·é€€è´§ï¼ˆå¤©ï¼‰
	 */
	private Integer finishOvertime;
	/**
	 * è®¢å•å®ŒæˆåŽè‡ªåŠ¨å¥½è¯„æ—¶é—´ï¼ˆå¤©ï¼‰
	 */
	private Integer commentOvertime;
	/**
	 * ä¼šå‘˜ç­‰çº§ã€0-ä¸é™ä¼šå‘˜ç­‰çº§ï¼Œå…¨éƒ¨é€šç”¨ï¼›å…¶ä»–-å¯¹åº”çš„å…¶ä»–ä¼šå‘˜ç­‰çº§ã€‘
	 */
	private Integer memberLevel;

}

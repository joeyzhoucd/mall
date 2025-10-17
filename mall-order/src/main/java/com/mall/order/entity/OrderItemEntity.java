package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * è®¢å•é¡¹ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Data
@TableName("oms_order_item")
public class OrderItemEntity implements Serializable {
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
	 * spu_id
	 */
	private Long spuId;
	/**
	 * spu_name
	 */
	private String spuName;
	/**
	 * spu_pic
	 */
	private String spuPic;
	/**
	 * å“ç‰Œ
	 */
	private String spuBrand;
	/**
	 * å•†å“åˆ†ç±»id
	 */
	private Long categoryId;
	/**
	 * å•†å“skuç¼–å·
	 */
	private Long skuId;
	/**
	 * å•†å“skuåå­—
	 */
	private String skuName;
	/**
	 * å•†å“skuå›¾ç‰‡
	 */
	private String skuPic;
	/**
	 * å•†å“skuä»·æ ¼
	 */
	private BigDecimal skuPrice;
	/**
	 * å•†å“è´­ä¹°çš„æ•°é‡
	 */
	private Integer skuQuantity;
	/**
	 * å•†å“é”€å”®å±žæ€§ç»„åˆï¼ˆJSONï¼‰
	 */
	private String skuAttrsVals;
	/**
	 * å•†å“ä¿ƒé”€åˆ†è§£é‡‘é¢
	 */
	private BigDecimal promotionAmount;
	/**
	 * ä¼˜æƒ åˆ¸ä¼˜æƒ åˆ†è§£é‡‘é¢
	 */
	private BigDecimal couponAmount;
	/**
	 * ç§¯åˆ†ä¼˜æƒ åˆ†è§£é‡‘é¢
	 */
	private BigDecimal integrationAmount;
	/**
	 * è¯¥å•†å“ç»è¿‡ä¼˜æƒ åŽçš„åˆ†è§£é‡‘é¢
	 */
	private BigDecimal realAmount;
	/**
	 * èµ é€ç§¯åˆ†
	 */
	private Integer giftIntegration;
	/**
	 * èµ é€æˆé•¿å€¼
	 */
	private Integer giftGrowth;

}

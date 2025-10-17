package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * ä¼˜æƒ åˆ¸åˆ†ç±»å…³è”
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_coupon_spu_category_relation")
public class CouponSpuCategoryRelationEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¼˜æƒ åˆ¸id
	 */
	private Long couponId;
	/**
	 * äº§å“åˆ†ç±»id
	 */
	private Long categoryId;
	/**
	 * äº§å“åˆ†ç±»åç§°
	 */
	private String categoryName;

}

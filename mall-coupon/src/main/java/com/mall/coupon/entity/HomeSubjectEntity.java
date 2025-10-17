package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * é¦–é¡µä¸“é¢˜è¡¨ã€jdé¦–é¡µä¸‹é¢å¾ˆå¤šä¸“é¢˜ï¼Œæ¯ä¸ªä¸“é¢˜é“¾æŽ¥æ–°çš„é¡µé¢ï¼Œå±•ç¤ºä¸“é¢˜å•†å“ä¿¡æ¯ã€‘
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@Data
@TableName("sms_home_subject")
public class HomeSubjectEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¸“é¢˜åå­—
	 */
	private String name;
	/**
	 * ä¸“é¢˜æ ‡é¢˜
	 */
	private String title;
	/**
	 * ä¸“é¢˜å‰¯æ ‡é¢˜
	 */
	private String subTitle;
	/**
	 * æ˜¾ç¤ºçŠ¶æ€
	 */
	private Integer status;
	/**
	 * è¯¦æƒ…è¿žæŽ¥
	 */
	private String url;
	/**
	 * æŽ’åº
	 */
	private Integer sort;
	/**
	 * ä¸“é¢˜å›¾ç‰‡åœ°å€
	 */
	private String img;

}

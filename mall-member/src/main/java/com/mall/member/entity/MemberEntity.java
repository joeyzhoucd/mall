package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ä¼šå‘˜
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_member")
public class MemberEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¼šå‘˜ç­‰çº§id
	 */
	private Long levelId;
	/**
	 * ç”¨æˆ·å
	 */
	private String username;
	/**
	 * å¯†ç 
	 */
	private String password;
	/**
	 * æ˜µç§°
	 */
	private String nickname;
	/**
	 * æ‰‹æœºå·ç 
	 */
	private String mobile;
	/**
	 * é‚®ç®±
	 */
	private String email;
	/**
	 * å¤´åƒ
	 */
	private String header;
	/**
	 * æ€§åˆ«
	 */
	private Integer gender;
	/**
	 * ç”Ÿæ—¥
	 */
	private Date birth;
	/**
	 * æ‰€åœ¨åŸŽå¸‚
	 */
	private String city;
	/**
	 * èŒä¸š
	 */
	private String job;
	/**
	 * ä¸ªæ€§ç­¾å
	 */
	private String sign;
	/**
	 * ç”¨æˆ·æ¥æº
	 */
	private Integer sourceType;
	/**
	 * ç§¯åˆ†
	 */
	private Integer integration;
	/**
	 * æˆé•¿å€¼
	 */
	private Integer growth;
	/**
	 * å¯ç”¨çŠ¶æ€
	 */
	private Integer status;
	/**
	 * æ³¨å†Œæ—¶é—´
	 */
	private Date createTime;

}

/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.renren.common.validator.group.AddGroup;
import io.renren.common.validator.group.UpdateGroup;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * ç³»ç»Ÿç”¨æˆ·
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_user")
public class SysUserEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * ç”¨æˆ·ID
	 */
	@TableId
	private Long userId;

	/**
	 * ç”¨æˆ·å
	 */
	@NotBlank(message="ç”¨æˆ·åä¸èƒ½ä¸ºç©º", groups = {AddGroup.class, UpdateGroup.class})
	private String username;

	/**
	 * å¯†ç 
	 */
	@NotBlank(message="å¯†ç ä¸èƒ½ä¸ºç©º", groups = AddGroup.class)
	private String password;

	/**
	 * ç›
	 */
	private String salt;

	/**
	 * é‚®ç®±
	 */
	@NotBlank(message="é‚®ç®±ä¸èƒ½ä¸ºç©º", groups = {AddGroup.class, UpdateGroup.class})
	@Email(message="é‚®ç®±æ ¼å¼ä¸æ­£ç¡®", groups = {AddGroup.class, UpdateGroup.class})
	private String email;

	/**
	 * æ‰‹æœºå·
	 */
	private String mobile;

	/**
	 * çŠ¶æ€  0ï¼šç¦ç”¨   1ï¼šæ­£å¸¸
	 */
	private Integer status;

	/**
	 * è§’è‰²IDåˆ—è¡¨
	 */
	@TableField(exist=false)
	private List<Long> roleIdList;

	/**
	 * åˆ›å»ºè€…ID
	 */
	private Long createUserId;

	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;

}

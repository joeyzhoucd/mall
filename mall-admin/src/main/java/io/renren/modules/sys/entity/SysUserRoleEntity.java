/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * ç”¨æˆ·ä¸Žè§’è‰²å¯¹åº”å…³ç³»
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_user_role")
public class SysUserRoleEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	@TableId
	private Long id;

	/**
	 * ç”¨æˆ·ID
	 */
	private Long userId;

	/**
	 * è§’è‰²ID
	 */
	private Long roleId;

	
}

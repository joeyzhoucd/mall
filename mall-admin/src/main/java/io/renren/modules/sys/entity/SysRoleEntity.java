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
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * è§’è‰²
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_role")
public class SysRoleEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * è§’è‰²ID
	 */
	@TableId
	private Long roleId;

	/**
	 * è§’è‰²åç§°
	 */
	@NotBlank(message="è§’è‰²åç§°ä¸èƒ½ä¸ºç©º")
	private String roleName;

	/**
	 * å¤‡æ³¨
	 */
	private String remark;
	
	/**
	 * åˆ›å»ºè€…ID
	 */
	private Long createUserId;

	@TableField(exist=false)
	private List<Long> menuIdList;
	
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;

	
}

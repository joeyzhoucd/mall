/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * ç³»ç»Ÿç”¨æˆ·Token
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_user_token")
public class SysUserTokenEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	//ç”¨æˆ·ID
	@TableId(type = IdType.INPUT)
	private Long userId;
	//token
	private String token;
	//è¿‡æœŸæ—¶é—´
	private Date expireTime;
	//æ›´æ–°æ—¶é—´
	private Date updateTime;

}

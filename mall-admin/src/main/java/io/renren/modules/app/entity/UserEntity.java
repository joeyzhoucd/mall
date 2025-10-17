/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.app.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * ç”¨æˆ·
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("tb_user")
public class UserEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * ç”¨æˆ·ID
	 */
	@TableId
	private Long userId;
	/**
	 * ç”¨æˆ·å
	 */
	private String username;
	/**
	 * æ‰‹æœºå·
	 */
	private String mobile;
	/**
	 * å¯†ç 
	 */
	private String password;
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	private Date createTime;

}

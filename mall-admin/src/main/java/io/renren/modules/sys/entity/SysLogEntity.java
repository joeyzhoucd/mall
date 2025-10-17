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
import java.util.Date;


/**
 * ç³»ç»Ÿæ—¥å¿—
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_log")
public class SysLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	@TableId
	private Long id;
	//ç”¨æˆ·å
	private String username;
	//ç”¨æˆ·æ“ä½œ
	private String operation;
	//è¯·æ±‚æ–¹æ³•
	private String method;
	//è¯·æ±‚å‚æ•°
	private String params;
	//æ‰§è¡Œæ—¶é•¿(æ¯«ç§’)
	private Long time;
	//IPåœ°å€
	private String ip;
	//åˆ›å»ºæ—¶é—´
	private Date createDate;

}

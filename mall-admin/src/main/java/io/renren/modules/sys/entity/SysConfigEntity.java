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

import javax.validation.constraints.NotBlank;

/**
 * ç³»ç»Ÿé…ç½®ä¿¡æ¯
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_config")
public class SysConfigEntity {
	@TableId
	private Long id;
	@NotBlank(message="å‚æ•°åä¸èƒ½ä¸ºç©º")
	private String paramKey;
	@NotBlank(message="å‚æ•°å€¼ä¸èƒ½ä¸ºç©º")
	private String paramValue;
	private String remark;

}

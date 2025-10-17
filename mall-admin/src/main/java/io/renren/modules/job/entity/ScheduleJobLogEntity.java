/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * å®šæ—¶ä»»åŠ¡æ—¥å¿—
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("schedule_job_log")
public class ScheduleJobLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * æ—¥å¿—id
	 */
	@TableId
	private Long logId;
	
	/**
	 * ä»»åŠ¡id
	 */
	private Long jobId;
	
	/**
	 * spring beanåç§°
	 */
	private String beanName;
	
	/**
	 * å‚æ•°
	 */
	private String params;
	
	/**
	 * ä»»åŠ¡çŠ¶æ€    0ï¼šæˆåŠŸ    1ï¼šå¤±è´¥
	 */
	private Integer status;
	
	/**
	 * å¤±è´¥ä¿¡æ¯
	 */
	private String error;
	
	/**
	 * è€—æ—¶(å•ä½ï¼šæ¯«ç§’)
	 */
	private Integer times;
	
	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date createTime;
	
}

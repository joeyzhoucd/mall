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

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;

/**
 * å®šæ—¶ä»»åŠ¡
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("schedule_job")
public class ScheduleJobEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * ä»»åŠ¡è°ƒåº¦å‚æ•°key
	 */
    public static final String JOB_PARAM_KEY = "JOB_PARAM_KEY";
	
	/**
	 * ä»»åŠ¡id
	 */
	@TableId
	private Long jobId;

	/**
	 * spring beanåç§°
	 */
	@NotBlank(message="beanåç§°ä¸èƒ½ä¸ºç©º")
	private String beanName;
	
	/**
	 * å‚æ•°
	 */
	private String params;
	
	/**
	 * cronè¡¨è¾¾å¼
	 */
	@NotBlank(message="cronè¡¨è¾¾å¼ä¸èƒ½ä¸ºç©º")
	private String cronExpression;

	/**
	 * ä»»åŠ¡çŠ¶æ€
	 */
	private Integer status;

	/**
	 * å¤‡æ³¨
	 */
	private String remark;

	/**
	 * åˆ›å»ºæ—¶é—´
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date createTime;

}

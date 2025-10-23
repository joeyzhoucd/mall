package io.renren.modules.job.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;

/**
 * Schedule job entity
 */
@Data
@TableName("schedule_job")
public class ScheduleJobEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * Job parameter key
	 */
    public static final String JOB_PARAM_KEY = "JOB_PARAM_KEY";
	
	/**
	 * Job ID
	 */
	@TableId
	private Long jobId;

	/**
	 * Bean name
	 */
	@NotBlank(message="Bean name cannot be empty")
	private String beanName;
	
	/**
	 * Parameters
	 */
	private String params;
	
	/**
	 * Cron expression
	 */
	@NotBlank(message="Cron expression cannot be empty")
	private String cronExpression;

	/**
	 * Status
	 */
	private Integer status;

	/**
	 * Remark
	 */
	private String remark;

	/**
	 * Create time
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date createTime;

}
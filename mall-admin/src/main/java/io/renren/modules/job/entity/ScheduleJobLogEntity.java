package io.renren.modules.job.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Schedule job log entity
 */
@Data
@TableName("schedule_job_log")
public class ScheduleJobLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * Log ID
	 */
	@TableId
	private Long logId;
	
	/**
	 * Job ID
	 */
	private Long jobId;
	
	/**
	 * Bean name
	 */
	private String beanName;
	
	/**
	 * Parameters
	 */
	private String params;
	
	/**
	 * Status
	 */
	private Integer status;
	
	/**
	 * Error message
	 */
	private String error;
	
	/**
	 * Execution times
	 */
	private Integer times;
	
	/**
	 * Create time
	 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private Date createTime;
	
}
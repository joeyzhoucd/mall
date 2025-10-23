package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * System log entity
 */
@Data
@TableName("sys_log")
public class SysLogEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	@TableId
	private Long id;
	// Username
	private String username;
	// User operation
	private String operation;
	// Request method
	private String method;
	// Request parameters
	private String params;
	// Execution time (ms)
	private Long time;
	// IP address
	private String ip;
	// Create time
	private Date createDate;

}
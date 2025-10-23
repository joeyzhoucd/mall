package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * System user token entity
 */
@Data
@TableName("sys_user_token")
public class SysUserTokenEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	// User ID
	@TableId(type = IdType.INPUT)
	private Long userId;
	// Token
	private String token;
	// Expire time
	private Date expireTime;
	// Update time
	private Date updateTime;

}
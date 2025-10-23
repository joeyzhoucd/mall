package io.renren.modules.app.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * User entity
 */
@Data
@TableName("tb_user")
public class UserEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * User ID
	 */
	@TableId
	private Long userId;
	
	/**
	 * Username
	 */
	private String username;
	
	/**
	 * Mobile number
	 */
	private String mobile;
	
	/**
	 * Password
	 */
	private String password;
	
	/**
	 * Create time
	 */
	private Date createTime;

}
package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.renren.common.validator.group.AddGroup;
import io.renren.common.validator.group.UpdateGroup;
import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * System user entity
 */
@Data
@TableName("sys_user")
public class SysUserEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * User ID
	 */
	@TableId
	private Long userId;

	/**
	 * Username
	 */
	@NotBlank(message="Username cannot be empty", groups = {AddGroup.class, UpdateGroup.class})
	private String username;

	/**
	 * Password
	 */
	@NotBlank(message="Password cannot be empty", groups = AddGroup.class)
	private String password;

	/**
	 * Salt
	 */
	private String salt;

	/**
	 * Email
	 */
	@NotBlank(message="Email cannot be empty", groups = {AddGroup.class, UpdateGroup.class})
	@Email(message="Email format is incorrect", groups = {AddGroup.class, UpdateGroup.class})
	private String email;

	/**
	 * Mobile
	 */
	private String mobile;

	/**
	 * Status
	 */
	private Integer status;

	/**
	 * Role ID list
	 */
	@TableField(exist=false)
	private List<Long> roleIdList;

	/**
	 * Creator ID
	 */
	private Long createUserId;

	/**
	 * Create time
	 */
	private Date createTime;

}
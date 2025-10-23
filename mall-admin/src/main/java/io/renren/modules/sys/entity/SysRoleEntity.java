package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * System role entity
 */
@Data
@TableName("sys_role")
public class SysRoleEntity implements Serializable {
	private static final long serialVersionUID = 1L;
	
	/**
	 * Role ID
	 */
	@TableId
	private Long roleId;

	/**
	 * Role name
	 */
	@NotBlank(message="Role name cannot be empty")
	private String roleName;

	/**
	 * Remark
	 */
	private String remark;
	
	/**
	 * Creator ID
	 */
	private Long createUserId;

	@TableField(exist=false)
	private List<Long> menuIdList;
	
	/**
	 * Create time
	 */
	private Date createTime;

}
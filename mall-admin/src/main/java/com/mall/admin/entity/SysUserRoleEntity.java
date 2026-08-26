package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_user_role
 */
@Data
@TableName("sys_user_role")
public class SysUserRoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long userId;

    private Long roleId;

}

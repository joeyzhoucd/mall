package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_user
 * 注意 salt 这一列：旧实现（Shiro）用它做 sha256(salt + password)。现在换成 BCrypt，
 * BCrypt 自带盐并把盐编码在哈希串里，这一列已经不再使用。
 * 保留字段是为了不改表结构（避免为一次哈希方案切换去做 schema 迁移），新建用户时留空。
 */
@Data
@TableName("sys_user")
public class SysUserEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long userId;

    private String username;

    private String password;

    private String salt;

    private String email;

    private String mobile;

    private Integer status;

    private Long createUserId;

    private java.util.Date createTime;


    /**
     * 角色 id 列表。不是表字段（关系在 sys_user_role 里），用 @TableField(exist = false)
     * 排除，否则 MyBatis-Plus 会把它当成一列去拼 SQL。
     * 列表接口返回 null，详情接口填充实际值 —— 这是前端 mock 里记录的形状。
     */
    @TableField(exist = false)
    private java.util.List<Long> roleIdList;
}

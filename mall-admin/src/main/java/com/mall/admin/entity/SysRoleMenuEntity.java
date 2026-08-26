package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_role_menu
 * 注意 menu_id 会存入 -666666 这个哨兵值（不是真实菜单 id），
 * 查询时必须按 id 升序返回以保持前端写入时的顺序。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenuEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long roleId;

    private Long menuId;

}

package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_menu
 * type: 0 目录、1 菜单、2 按钮。按钮不进导航树，只贡献 perms。
 */
@Data
@TableName("sys_menu")
public class SysMenuEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long menuId;

    private Long parentId;

    private String name;

    private String url;

    private String perms;

    private Integer type;

    private String icon;

    private Integer orderNum;


    /** 上级菜单名。表里没有这一列，列表接口现算现填。 */
    @TableField(exist = false)
    private String parentName;

    /**
     * 子节点。字段名必须叫 list —— 前端的 fnAddDynamicMenuRoutes 就是按这个名字递归的
     * （见 mall-frontend/src/router/index.js）。改成 children 会让二级菜单全部消失。
     */
    @TableField(exist = false)
    private java.util.List<SysMenuEntity> list;
}

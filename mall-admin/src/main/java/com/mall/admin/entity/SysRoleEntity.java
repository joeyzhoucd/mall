package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_role
 */
@Data
@TableName("sys_role")
public class SysRoleEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long roleId;

    private String roleName;

    private String remark;

    private Long createUserId;

    private java.util.Date createTime;


    /**
     * 菜单 id 列表。详情接口返回，形如 [...全勾选, -666666, ...半勾选]。
     * 必须【保序】且【保留 -666666 哨兵】，前端靠它区分两段。
     * 详见 openapi/admin-api.yaml 第六条。
     */
    @TableField(exist = false)
    private java.util.List<Long> menuIdList;
}

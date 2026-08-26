package com.mall.admin.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.admin.entity.SysRoleMenuEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysRoleMenuDao extends BaseMapper<SysRoleMenuEntity> {

    /**
     * 取某个角色的菜单 id，【按插入顺序】（id 升序）返回，且不过滤 -666666 哨兵。
     * 顺序和哨兵都是契约的一部分：前端用 indexOf(-666666) 的位置把列表截成
     * 「全勾选」和「半勾选」两段。过滤掉哨兵会让半勾选的父节点被当成完全勾选，
     * 不保序会让截断位置错位 —— 两种都不报错，只会让权限数据静默失真。
     * 详见 openapi/admin-api.yaml 第六条。
     */
    List<Long> selectMenuIdsByRoleId(@Param("roleId") Long roleId);
}

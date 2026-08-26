package com.mall.admin.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.admin.entity.SysUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserRoleDao extends BaseMapper<SysUserRoleEntity> {

    /** 取某个用户的角色 id。 */
    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);
}

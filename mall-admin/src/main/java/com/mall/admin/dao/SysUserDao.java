package com.mall.admin.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.admin.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserDao extends BaseMapper<SysUserEntity> {
}

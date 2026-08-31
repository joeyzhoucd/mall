package com.mall.admin.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.admin.entity.ScheduleJobEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ScheduleJobDao extends BaseMapper<ScheduleJobEntity> {
}

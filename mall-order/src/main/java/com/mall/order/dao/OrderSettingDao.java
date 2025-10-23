package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.OrderSettingEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface OrderSettingDao extends BaseMapper<OrderSettingEntity> {
	
}

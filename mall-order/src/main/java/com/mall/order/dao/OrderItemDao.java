package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.OrderItemEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface OrderItemDao extends BaseMapper<OrderItemEntity> {
	
}

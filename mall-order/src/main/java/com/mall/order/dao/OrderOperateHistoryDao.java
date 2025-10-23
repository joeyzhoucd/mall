package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.OrderOperateHistoryEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface OrderOperateHistoryDao extends BaseMapper<OrderOperateHistoryEntity> {
	
}

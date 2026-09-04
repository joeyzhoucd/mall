package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.OrderOutboxMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderOutboxMessageDao extends BaseMapper<OrderOutboxMessageEntity> {
}

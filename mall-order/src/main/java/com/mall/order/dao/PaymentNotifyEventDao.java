package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.PaymentNotifyEventEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentNotifyEventDao extends BaseMapper<PaymentNotifyEventEntity> {
}

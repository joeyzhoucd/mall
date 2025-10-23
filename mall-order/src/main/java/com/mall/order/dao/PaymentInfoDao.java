package com.mall.order.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.order.entity.PaymentInfoEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface PaymentInfoDao extends BaseMapper<PaymentInfoEntity> {
	
}

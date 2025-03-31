package com.joeyzhoucd.order.dao;

import com.joeyzhoucd.order.entity.OrderOperateHistoryEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单操作历史记录
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@Mapper
public interface OrderOperateHistoryDao extends BaseMapper<OrderOperateHistoryEntity> {
	
}

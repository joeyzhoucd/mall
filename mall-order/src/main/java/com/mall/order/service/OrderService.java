package com.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderEntity;

import java.util.Map;


public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

package com.joeyzhoucd.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.order.entity.OrderEntity;

import java.util.Map;

/**
 * 订单
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);
}


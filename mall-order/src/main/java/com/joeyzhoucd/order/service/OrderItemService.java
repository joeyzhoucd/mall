package com.joeyzhoucd.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.order.entity.OrderItemEntity;

import java.util.Map;

/**
 * 订单项信息
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
public interface OrderItemService extends IService<OrderItemEntity> {

    PageUtils queryPage(Map<String, Object> params);
}


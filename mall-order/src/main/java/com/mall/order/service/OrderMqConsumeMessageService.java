package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.order.entity.OrderMqConsumeMessageEntity;

public interface OrderMqConsumeMessageService extends IService<OrderMqConsumeMessageEntity> {

    boolean consumeOnce(String consumerGroup, String messageKey, String businessType, Runnable handler);
}

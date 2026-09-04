package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderOutboxMessageEntity;

import java.util.Map;

public interface OrderOutboxMessageService extends IService<OrderOutboxMessageEntity> {

    OrderOutboxMessageEntity enqueue(String messageKey,
                                     String businessType,
                                     String businessKey,
                                     String exchange,
                                     String routingKey,
                                     Object payload);

    int publishReadyMessages();

    boolean resend(Long id);

    boolean markDead(Long id, String reason);

    void markSent(Long id);

    void markFailed(Long id, String reason);

    PageUtils queryPage(Map<String, Object> params);
}

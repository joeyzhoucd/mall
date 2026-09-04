package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.order.entity.PaymentNotifyEventEntity;

public interface PaymentNotifyEventService extends IService<PaymentNotifyEventEntity> {

    boolean tryRecord(PaymentNotifyEventEntity event);

    void markProcessed(String eventKey, String processStatus, String processMessage);
}

package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.order.dao.PaymentNotifyEventDao;
import com.mall.order.entity.PaymentNotifyEventEntity;
import com.mall.order.service.PaymentNotifyEventService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service("paymentNotifyEventService")
public class PaymentNotifyEventServiceImpl
        extends ServiceImpl<PaymentNotifyEventDao, PaymentNotifyEventEntity>
        implements PaymentNotifyEventService {

    @Override
    public boolean tryRecord(PaymentNotifyEventEntity event) {
        if (event == null || event.getEventKey() == null || event.getEventKey().isBlank()) {
            throw new IllegalArgumentException("payment notify event key is required");
        }
        Date now = new Date();
        event.setProcessStatus("processing");
        event.setCreateTime(now);
        event.setUpdateTime(now);
        try {
            return this.save(event);
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void markProcessed(String eventKey, String processStatus, String processMessage) {
        PaymentNotifyEventEntity update = new PaymentNotifyEventEntity();
        update.setProcessStatus(processStatus);
        update.setProcessMessage(processMessage);
        update.setUpdateTime(new Date());
        this.update(update, new QueryWrapper<PaymentNotifyEventEntity>().eq("event_key", eventKey));
    }
}

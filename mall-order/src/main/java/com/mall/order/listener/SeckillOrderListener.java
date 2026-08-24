package com.mall.order.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.SeckillOrderTo;
import com.mall.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderListener {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = MqConstants.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderTo seckillOrderTo) {
        if (seckillOrderTo == null || seckillOrderTo.getLocalMessageId() == null) {
            return;
        }
        orderService.createSeckillOrder(seckillOrderTo);
    }
}

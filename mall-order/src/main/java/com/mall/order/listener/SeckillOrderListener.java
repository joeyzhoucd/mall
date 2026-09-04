package com.mall.order.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.SeckillOrderTo;
import com.mall.order.service.OrderMqConsumeMessageService;
import com.mall.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class SeckillOrderListener {

    private final OrderService orderService;
    private final OrderMqConsumeMessageService consumeMessageService;

    public SeckillOrderListener(OrderService orderService,
                                OrderMqConsumeMessageService consumeMessageService) {
        this.orderService = orderService;
        this.consumeMessageService = consumeMessageService;
    }

    @RabbitListener(queues = MqConstants.SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(SeckillOrderTo seckillOrderTo) {
        if (seckillOrderTo == null || seckillOrderTo.getLocalMessageId() == null) {
            return;
        }
        Long localMessageId = seckillOrderTo.getLocalMessageId();
        consumeMessageService.consumeOnce("seckill-order-listener", "seckill.order:" + localMessageId,
                "SECKILL_ORDER", () -> orderService.createSeckillOrder(seckillOrderTo));
    }
}

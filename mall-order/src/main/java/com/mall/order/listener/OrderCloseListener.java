package com.mall.order.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.OrderCloseTo;
import com.mall.order.service.OrderMqConsumeMessageService;
import com.mall.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseListener {

    private final OrderService orderService;
    private final OrderMqConsumeMessageService consumeMessageService;

    public OrderCloseListener(OrderService orderService,
                              OrderMqConsumeMessageService consumeMessageService) {
        this.orderService = orderService;
        this.consumeMessageService = consumeMessageService;
    }

    @RabbitListener(queues = MqConstants.ORDER_RELEASE_QUEUE)
    public void handleOrderClose(OrderCloseTo closeTo) {
        if (closeTo == null || closeTo.getOrderSn() == null) {
            return;
        }
        String orderSn = closeTo.getOrderSn();
        consumeMessageService.consumeOnce("order-close-listener", "order.close:" + orderSn,
                "ORDER_CLOSE", () -> orderService.closeOrder(orderSn));
    }
}


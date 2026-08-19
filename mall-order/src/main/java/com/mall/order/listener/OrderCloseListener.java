package com.mall.order.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.OrderCloseTo;
import com.mall.order.service.OrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderCloseListener {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = MqConstants.ORDER_RELEASE_QUEUE)
    public void handleOrderClose(OrderCloseTo closeTo) {
        if (closeTo == null || closeTo.getOrderSn() == null) {
            return;
        }
        orderService.closeOrder(closeTo.getOrderSn());
    }
}


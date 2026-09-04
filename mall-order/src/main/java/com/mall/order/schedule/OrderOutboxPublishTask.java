package com.mall.order.schedule;

import com.mall.order.service.OrderOutboxMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mall.order.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderOutboxPublishTask {

    private final OrderOutboxMessageService orderOutboxMessageService;

    public OrderOutboxPublishTask(OrderOutboxMessageService orderOutboxMessageService) {
        this.orderOutboxMessageService = orderOutboxMessageService;
    }

    @Scheduled(initialDelayString = "${mall.order.outbox.initial-delay-ms:10000}",
            fixedDelayString = "${mall.order.outbox.fixed-delay-ms:5000}")
    public void publishReadyMessages() {
        orderOutboxMessageService.publishReadyMessages();
    }
}

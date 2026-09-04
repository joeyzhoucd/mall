package com.mall.ware.schedule;

import com.mall.ware.service.StockOutboxMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mall.ware.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class StockOutboxPublishTask {

    private final StockOutboxMessageService stockOutboxMessageService;

    public StockOutboxPublishTask(StockOutboxMessageService stockOutboxMessageService) {
        this.stockOutboxMessageService = stockOutboxMessageService;
    }

    @Scheduled(initialDelayString = "${mall.ware.outbox.initial-delay-ms:10000}",
            fixedDelayString = "${mall.ware.outbox.fixed-delay-ms:5000}")
    public void publishReadyMessages() {
        stockOutboxMessageService.publishReadyMessages();
    }
}

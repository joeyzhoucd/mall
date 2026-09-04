package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.service.WareMqConsumeMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockFailListener {

    private final WareMqConsumeMessageService consumeMessageService;

    public StockFailListener(WareMqConsumeMessageService consumeMessageService) {
        this.consumeMessageService = consumeMessageService;
    }

    @RabbitListener(queues = MqConstants.STOCK_FAIL_QUEUE)
    public void handleStockFail(WareOrderTaskDetailEntity detail) {
        if (detail == null || detail.getId() == null) {
            return;
        }
        Long detailId = detail.getId();
        consumeMessageService.consumeOnce("stock-fail-listener", "stock.fail:" + detailId,
                "STOCK_FAIL", () -> log.warn("Stock task failed: detailId={}, skuId={}, taskId={}, retryCount={}",
                        detail.getId(), detail.getSkuId(), detail.getTaskId(), detail.getRetryCount()));
    }
}


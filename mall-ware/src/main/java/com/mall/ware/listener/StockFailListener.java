package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StockFailListener {

    @RabbitListener(queues = MqConstants.STOCK_FAIL_QUEUE)
    public void handleStockFail(WareOrderTaskDetailEntity detail) {
        if (detail == null) {
            return;
        }
        log.warn("Stock task failed: detailId={}, skuId={}, taskId={}, retryCount={}",
                detail.getId(), detail.getSkuId(), detail.getTaskId(), detail.getRetryCount());
    }
}


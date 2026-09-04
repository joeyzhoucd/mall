package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.StockReleaseTo;
import com.mall.ware.service.WareMqConsumeMessageService;
import com.mall.ware.service.WareSkuService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockReleaseListener {

    private final WareSkuService wareSkuService;
    private final WareMqConsumeMessageService consumeMessageService;

    public StockReleaseListener(WareSkuService wareSkuService,
                                WareMqConsumeMessageService consumeMessageService) {
        this.wareSkuService = wareSkuService;
        this.consumeMessageService = consumeMessageService;
    }

    @RabbitListener(queues = MqConstants.STOCK_RELEASE_QUEUE)
    public void handleStockRelease(StockReleaseTo releaseTo) {
        if (releaseTo == null || releaseTo.getOrderSn() == null) {
            return;
        }
        String orderSn = releaseTo.getOrderSn();
        consumeMessageService.consumeOnce("stock-release-listener", "stock.release:" + orderSn,
                "STOCK_RELEASE", () -> wareSkuService.unlockStock(releaseTo));
    }
}


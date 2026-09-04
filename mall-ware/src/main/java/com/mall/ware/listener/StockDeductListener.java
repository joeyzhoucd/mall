package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.StockDeductTo;
import com.mall.ware.service.WareMqConsumeMessageService;
import com.mall.ware.service.WareSkuService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class StockDeductListener {

    private final WareSkuService wareSkuService;
    private final WareMqConsumeMessageService consumeMessageService;

    public StockDeductListener(WareSkuService wareSkuService,
                               WareMqConsumeMessageService consumeMessageService) {
        this.wareSkuService = wareSkuService;
        this.consumeMessageService = consumeMessageService;
    }

    @RabbitListener(queues = MqConstants.STOCK_DEDUCT_QUEUE)
    public void handleStockDeduct(StockDeductTo deductTo) {
        if (deductTo == null || deductTo.getOrderSn() == null) {
            return;
        }
        String orderSn = deductTo.getOrderSn();
        consumeMessageService.consumeOnce("stock-deduct-listener", "stock.deduct:" + orderSn,
                "STOCK_DEDUCT", () -> wareSkuService.deductStock(deductTo));
    }
}


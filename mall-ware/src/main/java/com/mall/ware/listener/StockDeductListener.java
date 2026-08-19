package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.StockDeductTo;
import com.mall.ware.service.WareSkuService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StockDeductListener {

    @Autowired
    private WareSkuService wareSkuService;

    @RabbitListener(queues = MqConstants.STOCK_DEDUCT_QUEUE)
    public void handleStockDeduct(StockDeductTo deductTo) {
        wareSkuService.deductStock(deductTo);
    }
}


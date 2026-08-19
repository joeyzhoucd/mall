package com.mall.ware.listener;

import com.mall.common.constant.MqConstants;
import com.mall.common.to.StockReleaseTo;
import com.mall.ware.service.WareSkuService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StockReleaseListener {

    @Autowired
    private WareSkuService wareSkuService;

    @RabbitListener(queues = MqConstants.STOCK_RELEASE_QUEUE)
    public void handleStockRelease(StockReleaseTo releaseTo) {
        wareSkuService.unlockStock(releaseTo);
    }
}


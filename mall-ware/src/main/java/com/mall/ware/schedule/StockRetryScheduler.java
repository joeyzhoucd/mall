package com.mall.ware.schedule;

import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.entity.WareOrderTaskEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.service.WareSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockRetryScheduler {

    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Autowired
    private WareOrderTaskService wareOrderTaskService;

    @Autowired
    private WareSkuService wareSkuService;

    @Scheduled(fixedDelay = StockConstants.RETRY_INTERVAL_MS)
    public void retryStockOps() {
        List<WareOrderTaskDetailEntity> details = wareOrderTaskDetailService.listRetryingDetails(
                StockLockStatus.LOCKED,
                StockConstants.RETRY_LIMIT
        );
        for (WareOrderTaskDetailEntity detail : details) {
            if (detail == null) {
                continue;
            }
            WareOrderTaskEntity task = wareOrderTaskService.getById(detail.getTaskId());
            if (task == null || task.getOrderSn() == null) {
                continue;
            }
            StockReleaseItemTo itemTo = new StockReleaseItemTo();
            itemTo.setOrderSn(task.getOrderSn());
            itemTo.setSkuId(detail.getSkuId());
            itemTo.setCount(detail.getSkuNum());
            try {
                wareSkuService.retryStockOps(itemTo);
            } catch (Exception ignored) {
                // retry handled in service
            }
        }
    }
}


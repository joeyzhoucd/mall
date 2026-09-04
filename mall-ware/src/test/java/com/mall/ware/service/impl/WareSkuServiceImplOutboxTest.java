package com.mall.ware.service.impl;

import com.mall.common.constant.MqConstants;
import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.ware.dao.WareOrderTaskDetailDao;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.service.StockOutboxMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WareSkuServiceImplOutboxTest {

    private WareOrderTaskDetailDao detailDao;
    private StockOutboxMessageService outboxMessageService;
    private WareSkuServiceImpl service;

    @BeforeEach
    void setUp() {
        detailDao = mock(WareOrderTaskDetailDao.class);
        outboxMessageService = mock(StockOutboxMessageService.class);
        service = new WareSkuServiceImpl();
        ReflectionTestUtils.setField(service, "wareOrderTaskDetailDao", detailDao);
        ReflectionTestUtils.setField(service, "stockOutboxMessageService", outboxMessageService);
    }

    @Test
    void failedStockDetailEnqueuesOutboxMessageOnceAfterCasWins() {
        WareOrderTaskDetailEntity fresh = new WareOrderTaskDetailEntity();
        fresh.setId(12L);
        fresh.setSkuId(1001L);
        fresh.setSkuNum(2);
        fresh.setTaskId(30L);
        fresh.setLockStatus(StockLockStatus.LOCKED);
        fresh.setRetryCount(StockConstants.RETRY_LIMIT);

        when(detailDao.incrementRetryIfLocked(12L)).thenReturn(1);
        when(detailDao.selectById(12L)).thenReturn(fresh);
        when(detailDao.casLockStatus(12L, StockLockStatus.LOCKED, StockLockStatus.FAILED)).thenReturn(1);

        WareOrderTaskDetailEntity stale = new WareOrderTaskDetailEntity();
        stale.setId(12L);
        ReflectionTestUtils.invokeMethod(service, "increaseRetry", stale);

        verify(outboxMessageService).enqueue(
                eq("stock.fail.12"),
                eq("STOCK_FAIL"),
                eq("12"),
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_FAIL_ROUTING_KEY),
                eq(fresh)
        );
    }
}

package com.mall.ware.schedule;

import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.service.WareSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockRetrySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T08:00:00Z");

    private WareOrderTaskDetailService details;
    private WareSkuService wareSku;
    private StockRetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        details = mock(WareOrderTaskDetailService.class);
        wareSku = mock(WareSkuService.class);
        scheduler = new StockRetryScheduler();
        ReflectionTestUtils.setField(scheduler, "wareOrderTaskDetailService", details);
        ReflectionTestUtils.setField(scheduler, "wareOrderTaskService", mock(WareOrderTaskService.class));
        ReflectionTestUtils.setField(scheduler, "wareSkuService", wareSku);
        ReflectionTestUtils.setField(scheduler, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 只拿锁了 10 分钟以上的 —— 刚锁的可能属于还没提交的订单，查到 ORDER_NOT_FOUND 不代表订单不存在 */
    @Test
    void onlyLooksAtLocksOlderThanTheGracePeriod() {
        when(details.listRetryingDetails(any(), any(), any())).thenReturn(List.of());

        scheduler.retryStockOps();

        verify(details).listRetryingDetails(StockLockStatus.LOCKED, StockConstants.RETRY_LIMIT,
                Date.from(NOW.minusSeconds(600)));
    }

    /** FAILED 逐条交回正常流程；一条出错不影响其余 */
    @Test
    void failedDetailsAreHandedBackOneByOne() {
        when(details.listByLockStatus(StockLockStatus.FAILED)).thenReturn(List.of(detail(1L), detail(2L), detail(3L)));
        when(wareSku.manualRetryFailed(2L)).thenThrow(new RuntimeException("mall-order 仍不可达"));

        scheduler.recoverFailed();

        verify(wareSku, times(3)).manualRetryFailed(any());
        verify(wareSku).manualRetryFailed(eq(3L));
    }

    private static WareOrderTaskDetailEntity detail(long id) {
        WareOrderTaskDetailEntity d = new WareOrderTaskDetailEntity();
        d.setId(id);
        d.setLockStatus(StockLockStatus.FAILED);
        return d;
    }
}

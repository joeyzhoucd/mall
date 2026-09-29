package com.mall.order.service.impl;

import com.mall.common.metrics.BusinessFlow;
import com.mall.common.metrics.BusinessMetrics;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.feign.CouponFeignService;
import com.mall.order.feign.WareFeignService;
import com.mall.order.to.OrderCreateTo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用券 + 锁库存：结果不明的地方必须退券。
 *
 * <p>2026-09-29 下单压测：mall-ware 锁库存 p95 4s，mall-order 调它的熔断器 OPEN，
 * {@code orderLockStock} 抛 CallNotPermittedException。原代码只在它<b>返回</b>失败时退券，
 * 抛异常时直接冒出去 → 订单事务回滚、用户看到 500、券还占着（用券是 mall-coupon 已提交的 Feign 调用）。
 */
class ReserveCouponAndStockTest {

    private static final long HID = 77L, MEMBER = 8000001L;
    private static final String SN = "SN-RESERVE-1";

    private CouponFeignService coupon;
    private WareFeignService ware;
    private BusinessMetrics metrics;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        coupon = mock(CouponFeignService.class);
        ware = mock(WareFeignService.class);
        metrics = mock(BusinessMetrics.class);
        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "couponFeignService", coupon);
        ReflectionTestUtils.setField(service, "wareFeignService", ware);
        ReflectionTestUtils.setField(service, "businessMetrics", metrics);
        ReflectionTestUtils.setField(service, "internalToken", "t");
        when(coupon.useCoupon(anyLong(), anyLong(), any(), anyString(), anyString())).thenReturn(R.ok());
    }

    private static OrderCreateTo order() {
        OrderEntity o = new OrderEntity();
        o.setOrderSn(SN);
        o.setTotalAmount(new BigDecimal("100"));
        OrderItemEntity item = new OrderItemEntity();
        item.setSkuId(1001L);
        item.setSkuQuantity(1);
        item.setSkuName("x");
        OrderCreateTo to = new OrderCreateTo();
        to.setOrder(o);
        to.setOrderItems(List.of(item));
        return to;
    }

    @Test
    void lockThrowingReleasesTheCouponAndFailsCleanly() {
        when(ware.orderLockStock(any())).thenThrow(new RuntimeException(
                "CircuitBreaker 'WareFeignServiceorderLockStockWareSkuLockVo' is OPEN and does not permit further calls"));

        assertThat(service.reserveCouponAndStock(order(), HID, MEMBER)).as("结果码 6，不是异常/500").isEqualTo(6);

        verify(coupon).releaseCoupon(HID, SN, "t");
        verify(metrics).failure(BusinessFlow.ORDER_SUBMIT, OrderServiceImpl.REASON_STOCK_LOCK_UNAVAILABLE);
    }

    @Test
    void lockThrowingWithoutACouponReleasesNothing() {
        when(ware.orderLockStock(any())).thenThrow(new RuntimeException("Read timed out"));

        assertThat(service.reserveCouponAndStock(order(), null, MEMBER)).isEqualTo(6);

        verify(coupon, never()).releaseCoupon(any(), any(), any());
    }

    @Test
    void couponCallTimingOutIsReleasedBecauseItMayHaveBeenApplied() {
        when(coupon.useCoupon(anyLong(), anyLong(), any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Read timed out"));

        assertThat(service.reserveCouponAndStock(order(), HID, MEMBER)).isEqualTo(5);

        verify(coupon).releaseCoupon(HID, SN, "t");
        verifyNoInteractions(ware);
    }

    @Test
    void lockReturningFailureStillReleasesTheCoupon() {
        when(ware.orderLockStock(any())).thenReturn(R.error(21000, "库存不足"));

        assertThat(service.reserveCouponAndStock(order(), HID, MEMBER)).isEqualTo(3);

        verify(coupon).releaseCoupon(HID, SN, "t");
        verify(metrics).failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_STOCK_LOCK_FAILED);
    }

    @Test
    void bothReservedKeepsTheCoupon() {
        when(ware.orderLockStock(any())).thenReturn(R.ok());

        assertThat(service.reserveCouponAndStock(order(), HID, MEMBER)).isNull();

        verify(coupon, never()).releaseCoupon(any(), any(), any());
    }
}

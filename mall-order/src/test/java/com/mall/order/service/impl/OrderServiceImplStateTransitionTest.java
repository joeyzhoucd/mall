package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OrderStatus;
import com.mall.common.to.OrderOperateTo;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.service.OrderItemService;
import com.mall.order.service.OrderOutboxMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class OrderServiceImplStateTransitionTest {

    private OrderServiceImpl service;
    private OrderItemService orderItemService;
    private OrderOutboxMessageService orderOutboxMessageService;

    @BeforeEach
    void setUp() {
        service = spy(new OrderServiceImpl());
        orderItemService = mock(OrderItemService.class);
        orderOutboxMessageService = mock(OrderOutboxMessageService.class);
        ReflectionTestUtils.setField(service, "orderItemService", orderItemService);
        ReflectionTestUtils.setField(service, "orderOutboxMessageService", orderOutboxMessageService);
        doNothing().when(service).recordOperateHistory(any(OrderOperateTo.class));
    }

    @Test
    void paySuccessSendsStockDeductOnlyWhenNewToPayedCasWins() {
        doReturn(true).when(service).update(any(OrderEntity.class), any(Wrapper.class));
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderSn("O1");
        item.setSkuId(1001L);
        item.setSkuQuantity(2);
        doReturn(List.of(item)).when(orderItemService).list(any(Wrapper.class));

        service.payOrderSuccess("O1");

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        ArgumentCaptor<Wrapper<OrderEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(orderCaptor.capture(), wrapperCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.PAYED);
        assertThat(orderCaptor.getValue().getPaymentTime()).isNotNull();
        assertThat(wrapperCaptor.getValue().getCustomSqlSegment()).contains("order_sn", "status");
        verify(orderOutboxMessageService).enqueue(
                eq("stock.deduct.O1"),
                eq("STOCK_DEDUCT"),
                eq("O1"),
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_DEDUCT_ROUTING_KEY),
                any(Object.class));
        verify(orderOutboxMessageService, never()).enqueue(
                eq("stock.release.O1"),
                eq("STOCK_RELEASE"),
                eq("O1"),
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_RELEASE_ROUTING_KEY),
                any(Object.class));
    }

    @Test
    void paySuccessDoesNotDeductStockWhenCasLoses() {
        doReturn(false).when(service).update(any(OrderEntity.class), any(Wrapper.class));

        service.payOrderSuccess("O1");

        verify(orderItemService, never()).list(any(Wrapper.class));
        verify(orderOutboxMessageService, never()).enqueue(any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void closeOrderSendsStockReleaseOnlyWhenNewToClosedCasWins() {
        doReturn(true).when(service).update(any(OrderEntity.class), any(Wrapper.class));
        OrderItemEntity item = new OrderItemEntity();
        item.setOrderSn("O1");
        item.setSkuId(1001L);
        item.setSkuQuantity(2);
        doReturn(List.of(item)).when(orderItemService).list(any(Wrapper.class));

        service.closeOrder("O1");

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        ArgumentCaptor<Wrapper<OrderEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(orderCaptor.capture(), wrapperCaptor.capture());
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CLOSED);
        assertThat(orderCaptor.getValue().getPaymentTime()).isNull();
        assertThat(wrapperCaptor.getValue().getCustomSqlSegment()).contains("order_sn", "status");
        verify(orderOutboxMessageService).enqueue(
                eq("stock.release.O1"),
                eq("STOCK_RELEASE"),
                eq("O1"),
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_RELEASE_ROUTING_KEY),
                any(Object.class));
        verify(orderOutboxMessageService, never()).enqueue(
                eq("stock.deduct.O1"),
                eq("STOCK_DEDUCT"),
                eq("O1"),
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_DEDUCT_ROUTING_KEY),
                any(Object.class));
    }

    @Test
    void closeOrderDoesNotReleaseStockWhenCasLoses() {
        doReturn(false).when(service).update(any(OrderEntity.class), any(Wrapper.class));

        service.closeOrder("O1");

        verify(orderItemService, never()).list(any(Wrapper.class));
        verify(orderOutboxMessageService, never()).enqueue(any(String.class), any(String.class), any(String.class),
                any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void shipOrderTransitionsPayedToSentWithDeliverySnapshot() {
        doReturn(true).when(service).update(any(OrderEntity.class), any(Wrapper.class));

        boolean updated = service.shipOrder("O1", "SF", "SF100");

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        ArgumentCaptor<Wrapper<OrderEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(orderCaptor.capture(), wrapperCaptor.capture());
        assertThat(updated).isTrue();
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.SENT);
        assertThat(orderCaptor.getValue().getDeliveryCompany()).isEqualTo("SF");
        assertThat(orderCaptor.getValue().getDeliverySn()).isEqualTo("SF100");
        assertThat(orderCaptor.getValue().getDeliveryTime()).isNotNull();
        assertThat(wrapperCaptor.getValue().getCustomSqlSegment()).contains("order_sn", "status");
    }

    @Test
    void receiveOrderTransitionsSentToReceived() {
        doReturn(true).when(service).update(any(OrderEntity.class), any(Wrapper.class));

        boolean updated = service.receiveOrder("O1");

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(service).update(orderCaptor.capture(), any(Wrapper.class));
        assertThat(updated).isTrue();
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(orderCaptor.getValue().getReceiveTime()).isNotNull();
    }

    @Test
    void afterSaleTransitionsUseTheSameCasGate() {
        doReturn(true).when(service).update(any(OrderEntity.class), any(Wrapper.class));

        assertThat(service.startAfterSale("O1", "refund requested")).isTrue();
        assertThat(service.finishAfterSale("O1", "refund done")).isTrue();

        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(service, org.mockito.Mockito.times(2)).update(orderCaptor.capture(), any(Wrapper.class));
        assertThat(orderCaptor.getAllValues().get(0).getStatus()).isEqualTo(OrderStatus.SERVICING);
        assertThat(orderCaptor.getAllValues().get(1).getStatus()).isEqualTo(OrderStatus.SERVICED);
    }
}

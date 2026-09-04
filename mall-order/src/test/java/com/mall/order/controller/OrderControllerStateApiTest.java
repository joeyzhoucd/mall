package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.OrderStatus;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderEntity;
import com.mall.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderControllerStateApiTest {

    private OrderController controller;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        controller = new OrderController();
        orderService = mock(OrderService.class);
        ReflectionTestUtils.setField(controller, "orderService", orderService);
    }

    @Test
    void statusResponseIncludesStateNameAndAllowedTargets() {
        OrderEntity order = new OrderEntity();
        order.setOrderSn("O1");
        order.setStatus(OrderStatus.PAYED);
        when(orderService.getOrderBySn("O1")).thenReturn(order);

        R response = controller.getOrderStatus("O1");

        assertThat(response.getCode()).isZero();
        assertThat(response.get("status")).isEqualTo(OrderStatus.PAYED);
        assertThat(response.get("statusName")).isEqualTo("PAYED");
        assertThat(response.get("allowedTargets")).isEqualTo(List.of(OrderStatus.SENT, OrderStatus.SERVICING));
    }

    @Test
    void statusMetadataExposesDefinitionsAndTransitionTable() {
        R response = controller.getOrderStatuses();

        assertThat(response.getCode()).isZero();
        assertThat(response.get("statuses")).isEqualTo(OrderStatus.definitions());
        assertThat(response.get("transitionTable")).isEqualTo(OrderStatus.transitionTable());
        assertThat(response.get("transitions")).isEqualTo(OrderStatus.transitions());
    }

    @Test
    void shipOrderDelegatesToProtectedServiceTransition() {
        when(orderService.shipOrder("O1", "SF", "SF100")).thenReturn(true);

        R response = controller.shipOrder(new OrderController.ShipOrderRequest("O1", "SF", "SF100"));

        assertThat(response.getCode()).isZero();
        verify(orderService).shipOrder("O1", "SF", "SF100");
    }

    @Test
    void receiveOrderReturnsIllegalTransitionWhenCasDoesNotUpdate() {
        when(orderService.receiveOrder("O1")).thenReturn(false);

        R response = controller.receiveOrder(new OrderController.OrderSnRequest("O1"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.ORDER_STATUS_TRANSITION_ILLEGAL.getCode());
    }

    @Test
    void afterSaleEndpointsDelegateToProtectedServiceTransitions() {
        when(orderService.startAfterSale("O1", "refund requested")).thenReturn(true);
        when(orderService.finishAfterSale("O1", "refund done")).thenReturn(true);

        assertThat(controller.startAfterSale(new OrderController.AfterSaleRequest("O1", "refund requested")).getCode()).isZero();
        assertThat(controller.finishAfterSale(new OrderController.AfterSaleRequest("O1", "refund done")).getCode()).isZero();
        verify(orderService).startAfterSale("O1", "refund requested");
        verify(orderService).finishAfterSale("O1", "refund done");
    }
}

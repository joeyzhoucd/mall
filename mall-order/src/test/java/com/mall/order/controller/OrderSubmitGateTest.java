package com.mall.order.controller;

import com.mall.common.metrics.BusinessFlow;
import com.mall.common.metrics.BusinessMetrics;
import com.mall.common.utils.R;
import com.mall.order.service.OrderService;
import com.mall.order.submit.SubmitGate;
import com.mall.order.vo.OrderSubmitVo;
import com.mall.order.vo.SubmitOrderResponseVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 下单入口的闸门：拒绝时不碰下单逻辑；放进来的无论成败都归还名额 */
class OrderSubmitGateTest {

    private OrderService orderService;
    private BusinessMetrics metrics;
    private SubmitGate gate;
    private OrderWebController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        metrics = mock(BusinessMetrics.class);
        gate = new SubmitGate(1);
        controller = new OrderWebController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "submitGate", gate);
        ReflectionTestUtils.setField(controller, "businessMetrics", metrics);
    }

    private String submit(MockHttpServletResponse response, RedirectAttributesModelMap flash) {
        return controller.submitOrderPage(new OrderSubmitVo(), flash, new MockHttpServletRequest("POST", "/order/submitOrder"), response);
    }

    @Test
    void rejectedRequestNeverReachesSubmitOrderSoTheTokenIsNotConsumed() {
        assertThat(gate.tryEnter()).isTrue();                      // 名额被占满
        MockHttpServletResponse response = new MockHttpServletResponse();
        RedirectAttributesModelMap flash = new RedirectAttributesModelMap();

        String view = submit(response, flash);

        assertThat(view).startsWith("redirect:").endsWith("/order/confirm.html");
        assertThat(flash.getFlashAttributes().get("errorMsg")).isEqualTo("当前下单人数较多，请稍后再试");
        assertThat(response.getHeader(SubmitGate.DEGRADED_HEADER)).isEqualTo(SubmitGate.DEGRADED_VALUE);
        verifyNoInteractions(orderService);
        verify(metrics).failure(BusinessFlow.ORDER_SUBMIT, SubmitGate.REASON_BUSY);
        assertThat(gate.inFlight()).as("被拒的不占名额").isEqualTo(1);
    }

    /** JSON 入口（/order/submit）和表单入口共用一个闸门 —— 原来它绕过了闸门 */
    @Test
    void jsonEndpointIsGatedToo() {
        assertThat(gate.tryEnter()).isTrue();
        MockHttpServletResponse response = new MockHttpServletResponse();

        R r = controller.submitOrder(new OrderSubmitVo(), response);

        assertThat(r.getCode()).isEqualTo(SubmitGate.BUSY_CODE);
        assertThat(response.getHeader(SubmitGate.DEGRADED_HEADER)).isEqualTo(SubmitGate.DEGRADED_VALUE);
        verifyNoInteractions(orderService);
    }

    @Test
    void jsonEndpointReleasesItsSlot() {
        when(orderService.submitOrder(any())).thenThrow(new RuntimeException("boom"));
        assertThatThrownBy(() -> controller.submitOrder(new OrderSubmitVo(), new MockHttpServletResponse()));
        assertThat(gate.inFlight()).isZero();
    }

    @Test
    void admittedRequestReleasesItsSlotEvenWhenSubmitThrows() {
        when(orderService.submitOrder(any())).thenThrow(new RuntimeException("Connection is not available"));

        assertThatThrownBy(() -> submit(new MockHttpServletResponse(), new RedirectAttributesModelMap()))
                .hasMessageContaining("Connection is not available");

        assertThat(gate.inFlight()).as("漏还的话闸门越关越小，最后全部拒绝、只能重启").isZero();
    }

    @Test
    void admittedRequestReleasesItsSlotOnNormalFailure() {
        SubmitOrderResponseVo stockFailed = new SubmitOrderResponseVo();
        stockFailed.setCode(3);
        when(orderService.submitOrder(any())).thenReturn(stockFailed);
        MockHttpServletResponse response = new MockHttpServletResponse();

        submit(response, new RedirectAttributesModelMap());

        assertThat(gate.inFlight()).isZero();
        assertThat(response.getHeader(SubmitGate.DEGRADED_HEADER)).as("业务失败不是闸门拒绝").isNull();
    }
}

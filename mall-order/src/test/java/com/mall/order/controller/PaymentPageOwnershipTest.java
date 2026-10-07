package com.mall.order.controller;

import com.mall.order.entity.OrderEntity;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderService;
import com.mall.order.to.UserInfoTo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 支付页的订单归属校验（2026-10-06）。之前任何登录用户拿到订单号就能看别人的单，
 * 还能拿到服务端签好名的「支付成功 / 关闭交易」表单。
 * 关键断言是「别人的单」和「不存在的单」结果完全一样，而且 model 里一个签名都没有。
 */
class PaymentPageOwnershipTest {

    private static final long OWNER = 101L;
    private static final long OTHER = 202L;

    private OrderService orderService;
    private OrderWebController controller;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        controller = new OrderWebController();
        ReflectionTestUtils.setField(controller, "orderService", orderService);
        ReflectionTestUtils.setField(controller, "signKey", "test-only-pay-mock-sign-key");

        OrderEntity order = new OrderEntity();
        order.setOrderSn("SN-OWNED");
        order.setMemberId(OWNER);
        order.setPayAmount(new BigDecimal("12.30"));
        when(orderService.getOrderBySn("SN-OWNED")).thenReturn(order);
    }

    @AfterEach
    void clear() {
        OrderInterceptor.threadLocal.remove();
    }

    private static void loginAs(long userId) {
        UserInfoTo u = new UserInfoTo();
        u.setUserId(userId);
        OrderInterceptor.threadLocal.set(u);
    }

    private String open(String orderSn, ExtendedModelMap model) {
        return controller.paymentPage(orderSn, model, new MockHttpServletRequest("GET", "/order/payment.html"));
    }

    @Test
    void ownerSeesOrderAndSignedForms() {
        loginAs(OWNER);
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(open("SN-OWNED", model)).isEqualTo("orderPayment");
        assertThat(model).containsKeys("order", "signSuccess", "signClosed", "signFail");
    }

    @Test
    void otherUserGetsExactlyTheNotFoundResultAndNoSignatures() {
        loginAs(OTHER);
        ExtendedModelMap foreign = new ExtendedModelMap();
        String foreignView = open("SN-OWNED", foreign);

        ExtendedModelMap missing = new ExtendedModelMap();
        String missingView = open("SN-DOES-NOT-EXIST", missing);

        assertThat(foreignView).startsWith("redirect:").endsWith("/order/confirm.html");
        assertThat(foreignView).as("不能和「不存在」有任何区别，否则就泄露了订单号是否存在").isEqualTo(missingView);
        assertThat(foreign).doesNotContainKeys("order", "signSuccess", "signClosed", "signFail");
    }
}

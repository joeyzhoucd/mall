package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.OrderStatus;
import com.mall.order.client.PaymentGatewayClient;
import com.mall.order.config.PaymentGatewayProperties;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.entity.RefundInfoEntity;
import com.mall.order.service.OrderService;
import com.mall.order.service.PaymentInfoService;
import com.mall.order.service.PaymentNotifyEventService;
import com.mall.order.service.RefundInfoService;
import com.mall.order.util.PaySignUtils;
import com.mall.order.vo.pay.CreateOrderPaymentRequest;
import com.mall.order.vo.pay.PaymentGatewayRequest;
import com.mall.order.vo.pay.PaymentGatewayResponse;
import com.mall.order.vo.pay.PaymentNotifyRequest;
import com.mall.order.vo.pay.PaymentNotifyResult;
import com.mall.order.vo.pay.PaymentRefundGatewayResponse;
import com.mall.order.vo.pay.RefundOrderPaymentRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPaymentServiceImplTest {

    private static final String SIGN_KEY = "test-sign-key";

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentInfoService paymentInfoService;

    @Mock
    private PaymentNotifyEventService paymentNotifyEventService;

    @Mock
    private RefundInfoService refundInfoService;

    @Mock
    private OrderService orderService;

    private OrderPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderPaymentServiceImpl(
                paymentGatewayClient,
                new PaymentGatewayProperties("http://payment", "http://order/notify", "http://order/return", SIGN_KEY),
                paymentInfoService,
                paymentNotifyEventService,
                refundInfoService,
                orderService,
                new ObjectMapper()
        );
    }

    @Test
    void createsPaymentAndPersistsPaymentInfo() {
        OrderEntity order = new OrderEntity();
        order.setId(10L);
        order.setOrderSn("ORD-2001");
        order.setPayAmount(new BigDecimal("99.90"));
        when(orderService.getOrderBySn("ORD-2001")).thenReturn(order);
        when(paymentGatewayClient.createPayment(any())).thenReturn(new PaymentGatewayResponse(
                "alipay",
                "ORD-2001",
                "ALIORD2001",
                "pending",
                new BigDecimal("99.90"),
                "CNY",
                "Mall order ORD-2001",
                "http://pay",
                "qr",
                null,
                false,
                "signed",
                "sign",
                java.util.Map.of("code", "10000"),
                Instant.now(),
                Instant.now()
        ));

        PaymentGatewayResponse response = service.createPayment(
                "ORD-2001",
                new CreateOrderPaymentRequest("alipay", "CNY", null, null, null, null)
        );

        assertThat(response.tradeNo()).isEqualTo("ALIORD2001");
        ArgumentCaptor<PaymentGatewayRequest> requestCaptor = ArgumentCaptor.forClass(PaymentGatewayRequest.class);
        verify(paymentGatewayClient).createPayment(requestCaptor.capture());
        assertThat(requestCaptor.getValue().notifyUrl()).isEqualTo("http://order/notify");
        assertThat(requestCaptor.getValue().returnUrl()).isEqualTo("http://order/return");

        ArgumentCaptor<PaymentInfoEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentInfoEntity.class);
        verify(paymentInfoService).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getOrderSn()).isEqualTo("ORD-2001");
        assertThat(paymentCaptor.getValue().getAlipayTradeNo()).isEqualTo("ALIORD2001");
        assertThat(paymentCaptor.getValue().getPaymentChannel()).isEqualTo("alipay");
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo("pending");
    }

    @Test
    void queryPaymentAppliesTerminalOrderTransition() {
        OrderEntity order = new OrderEntity();
        order.setId(14L);
        order.setOrderSn("ORD-2005");
        when(orderService.getOrderBySn("ORD-2005")).thenReturn(order);
        when(paymentGatewayClient.queryPayment("wechat", "ORD-2005")).thenReturn(new PaymentGatewayResponse(
                "wechat",
                "ORD-2005",
                "WXORD2005",
                "success",
                new BigDecimal("50.00"),
                "CNY",
                "Mall order ORD-2005",
                null,
                null,
                null,
                false,
                "signed",
                "sign",
                null,
                Instant.now(),
                Instant.now()
        ));

        PaymentGatewayResponse response = service.queryPayment("wechat", "ORD-2005");

        assertThat(response.status()).isEqualTo("success");
        verify(orderService).payOrderSuccess("ORD-2005");
        ArgumentCaptor<PaymentInfoEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentInfoEntity.class);
        verify(paymentInfoService).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentChannel()).isEqualTo("wechat");
    }

    @Test
    void reconcilesPendingPaymentsByQueryingGateway() {
        PaymentInfoEntity pending = new PaymentInfoEntity();
        pending.setOrderSn("ORD-2006");
        pending.setPaymentChannel("alipay");
        pending.setPaymentStatus("pending");
        when(paymentInfoService.listPendingPaymentsForReconciliation(any(Date.class), anyInt()))
                .thenReturn(List.of(pending));

        OrderEntity order = new OrderEntity();
        order.setId(15L);
        order.setOrderSn("ORD-2006");
        when(orderService.getOrderBySn("ORD-2006")).thenReturn(order);
        when(paymentGatewayClient.queryPayment("alipay", "ORD-2006")).thenReturn(new PaymentGatewayResponse(
                "alipay",
                "ORD-2006",
                "ALIORD2006",
                "success",
                new BigDecimal("60.00"),
                "CNY",
                "Mall order ORD-2006",
                null,
                null,
                null,
                false,
                "signed",
                "sign",
                null,
                Instant.now(),
                Instant.now()
        ));

        int reconciled = service.reconcilePendingPayments(new Date(), 100);

        assertThat(reconciled).isEqualTo(1);
        verify(paymentGatewayClient).queryPayment("alipay", "ORD-2006");
        verify(orderService).payOrderSuccess("ORD-2006");
    }

    @Test
    void refundPersistsRefundInfoForReconciliation() {
        PaymentInfoEntity existing = new PaymentInfoEntity();
        existing.setOrderSn("ORD-2007");
        existing.setPaymentStatus("success");
        when(paymentInfoService.getOne(any(Wrapper.class))).thenReturn(existing);
        when(paymentGatewayClient.refund(any())).thenReturn(new PaymentRefundGatewayResponse(
                "wechat",
                "ORD-2007",
                "WXORD2007",
                "RF-2007",
                "WXRRF2007",
                "refunded",
                new BigDecimal("7.00"),
                "CNY",
                false,
                "signed",
                "sign",
                java.util.Map.of("status", "SUCCESS"),
                Instant.now()
        ));

        PaymentRefundGatewayResponse response = service.refund(new RefundOrderPaymentRequest(
                "wechat",
                "ORD-2007",
                "WXORD2007",
                "RF-2007",
                new BigDecimal("7.00"),
                "user request"
        ));

        assertThat(response.paymentStatus()).isEqualTo("refunded");
        verify(paymentInfoService).updateById(existing);
        ArgumentCaptor<RefundInfoEntity> refundCaptor = ArgumentCaptor.forClass(RefundInfoEntity.class);
        verify(refundInfoService).save(refundCaptor.capture());
        assertThat(refundCaptor.getValue().getOrderSn()).isEqualTo("ORD-2007");
        assertThat(refundCaptor.getValue().getPaymentChannel()).isEqualTo("wechat");
        assertThat(refundCaptor.getValue().getRefundSn()).isEqualTo("RF-2007");
        assertThat(refundCaptor.getValue().getRefundTradeNo()).isEqualTo("WXRRF2007");
        assertThat(refundCaptor.getValue().getRefundStatus()).isEqualTo(1);
    }

    @Test
    void successfulNotifyUpdatesOrderOnce() {
        OrderEntity order = new OrderEntity();
        order.setId(11L);
        order.setOrderSn("ORD-2002");
        order.setStatus(OrderStatus.NEW);
        when(orderService.getOrderBySn("ORD-2002")).thenReturn(order);

        String signedContent = "channel=wechat&orderSn=ORD-2002&tradeNo=WXORD2002&status=success&amount=20.00&currency=CNY";
        PaymentNotifyRequest request = new PaymentNotifyRequest(
                "wechat",
                "ORD-2002",
                "WXORD2002",
                "SUCCESS",
                new BigDecimal("20.00"),
                "CNY",
                "2026-09-03T00:00:00Z",
                signedContent,
                PaySignUtils.hmacSha256(signedContent, SIGN_KEY)
        );
        when(paymentNotifyEventService.tryRecord(any())).thenReturn(true);

        PaymentNotifyResult first = service.handleNotify(request);

        assertThat(first.accepted()).isTrue();
        assertThat(first.idempotent()).isFalse();
        verify(orderService).payOrderSuccess("ORD-2002");
        verify(paymentInfoService).save(any(PaymentInfoEntity.class));
        verify(paymentNotifyEventService).markProcessed(
                "wechat|WXORD2002|SUCCESS",
                "processed",
                "payment status success"
        );
    }

    @Test
    void duplicateTerminalNotifyDoesNotUpdateOrderAgain() {
        OrderEntity order = new OrderEntity();
        order.setId(12L);
        order.setOrderSn("ORD-2003");
        when(orderService.getOrderBySn("ORD-2003")).thenReturn(order);

        PaymentInfoEntity existing = new PaymentInfoEntity();
        existing.setOrderSn("ORD-2003");
        existing.setPaymentStatus("success");
        when(paymentInfoService.getOne(any(Wrapper.class))).thenReturn(existing);
        when(paymentNotifyEventService.tryRecord(any())).thenReturn(true);

        String signedContent = "channel=alipay&orderSn=ORD-2003&tradeNo=ALIORD2003&status=success&amount=30.00&currency=CNY";
        PaymentNotifyResult result = service.handleNotify(new PaymentNotifyRequest(
                "alipay",
                "ORD-2003",
                "ALIORD2003",
                "TRADE_SUCCESS",
                new BigDecimal("30.00"),
                "CNY",
                "2026-09-03T00:00:00Z",
                signedContent,
                PaySignUtils.hmacSha256(signedContent, SIGN_KEY)
        ));

        assertThat(result.idempotent()).isTrue();
        verify(orderService, never()).payOrderSuccess("ORD-2003");
        verify(paymentInfoService, never()).updateById(any(PaymentInfoEntity.class));
        verify(paymentNotifyEventService).markProcessed(
                "alipay|ALIORD2003|TRADE_SUCCESS",
                "ignored",
                "order payment already success"
        );
    }

    @Test
    void duplicateNotifyEventDoesNotTouchPaymentOrOrder() {
        OrderEntity order = new OrderEntity();
        order.setId(13L);
        order.setOrderSn("ORD-2004");
        when(orderService.getOrderBySn("ORD-2004")).thenReturn(order);
        when(paymentNotifyEventService.tryRecord(any())).thenReturn(false);

        String signedContent = "channel=wechat&orderSn=ORD-2004&tradeNo=WXORD2004&status=success&amount=40.00&currency=CNY";
        PaymentNotifyResult result = service.handleNotify(new PaymentNotifyRequest(
                "wechat",
                "ORD-2004",
                "WXORD2004",
                "SUCCESS",
                new BigDecimal("40.00"),
                "CNY",
                "2026-09-03T00:00:00Z",
                signedContent,
                PaySignUtils.hmacSha256(signedContent, SIGN_KEY)
        ));

        assertThat(result.accepted()).isTrue();
        assertThat(result.idempotent()).isTrue();
        assertThat(result.message()).isEqualTo("duplicate notify event");
        verify(paymentInfoService, never()).save(any(PaymentInfoEntity.class));
        verify(orderService, never()).payOrderSuccess("ORD-2004");
        verify(paymentNotifyEventService, never()).markProcessed(any(), any(), any());
    }
}

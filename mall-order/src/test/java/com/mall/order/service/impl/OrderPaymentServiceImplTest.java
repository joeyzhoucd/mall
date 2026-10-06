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

    // ---------------------------------------------------------------------
    // 签名内容与请求字段必须一致（2026-10-06）。之前只验 signedContent 和 sign 是否匹配，
    // 执行时却用请求里另外传的字段 —— 一条合法签名能配任意订单号 / 状态重放。
    // 每个篡改用例都用一条【签名本身完全合法】的 signedContent，只改请求字段；
    // 并断言订单/支付记录/回调事件一概没被碰过。
    // ---------------------------------------------------------------------

    private static final String GENUINE_PENDING =
            "channel=alipay&orderSn=ORD-3001&tradeNo=ALIORD3001&status=pending&amount=10.00&currency=CNY";

    private PaymentNotifyRequest notify(String channel, String orderSn, String tradeNo, String tradeStatus,
                                        String amount, String currency, String signedContent) {
        return new PaymentNotifyRequest(channel, orderSn, tradeNo, tradeStatus,
                amount == null ? null : new BigDecimal(amount), currency, "2026-10-06T00:00:00Z",
                signedContent, PaySignUtils.hmacSha256(signedContent, SIGN_KEY));
    }

    private void assertRejectedUntouched(PaymentNotifyRequest request) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.handleNotify(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match signed content");
        org.mockito.Mockito.verifyNoInteractions(orderService, paymentInfoService, paymentNotifyEventService);
    }

    @Test
    void rejectsGenuineSignatureReplayedForAnotherOrder() {
        // 攻击原型：拿 ORD-3001 的合法 pending 签名，去把 ORD-9999 置为已支付
        assertRejectedUntouched(notify("alipay", "ORD-9999", "ALIORD3001", "TRADE_SUCCESS", "10.00", "CNY", GENUINE_PENDING));
    }

    @Test
    void rejectsPendingSignatureClaimingSuccess() {
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIORD3001", "TRADE_SUCCESS", "10.00", "CNY", GENUINE_PENDING));
    }

    @Test
    void rejectsTamperedAmountTradeNoChannelOrCurrency() {
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIORD3001", "WAIT_BUYER_PAY", "0.01", "CNY", GENUINE_PENDING));
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIOTHER", "WAIT_BUYER_PAY", "10.00", "CNY", GENUINE_PENDING));
        assertRejectedUntouched(notify("wechat", "ORD-3001", "ALIORD3001", "NOTPAY", "10.00", "CNY", GENUINE_PENDING));
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIORD3001", "WAIT_BUYER_PAY", "10.00", "USD", GENUINE_PENDING));
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIORD3001", "WAIT_BUYER_PAY", null, "CNY", GENUINE_PENDING));
    }

    @Test
    void rejectsSignedContentMissingAField() {
        // 签名里没写 orderSn —— 「没写」不能变成「不用核对」
        String noOrderSn = "channel=alipay&tradeNo=ALIORD3001&status=pending&amount=10.00&currency=CNY";
        assertRejectedUntouched(notify("alipay", "ORD-3001", "ALIORD3001", "WAIT_BUYER_PAY", "10.00", "CNY", noOrderSn));
    }

    /**
     * 防误杀：mall-payment 真实发出的每一种 (渠道状态码, 签名内部码) 组合都必须通过核对。
     * 这张表照抄 mall-payment PaymentMockService.providerStatus 和 PaymentStatus 的 code；
     * 那边改了映射这里要跟着改，否则合法回调会被当成篡改拒掉。
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "alipay,WAIT_BUYER_PAY,pending", "alipay,TRADE_SUCCESS,success", "alipay,TRADE_CLOSED,closed", "alipay,TRADE_FINISHED,refunded",
            "wechat,NOTPAY,pending", "wechat,SUCCESS,success", "wechat,CLOSED,closed", "wechat,REFUND,refunded",
            "credit_card,requires_confirmation,pending", "credit_card,succeeded,success", "credit_card,canceled,closed", "credit_card,refunded,refunded"
    })
    void acceptsEveryGenuineGatewayStatusPair(String channel, String providerStatus, String signedStatus) {
        OrderEntity order = new OrderEntity();
        order.setOrderSn("ORD-3002");
        when(orderService.getOrderBySn("ORD-3002")).thenReturn(order);
        // 返回 false = 重复事件，handleNotify 在核对通过之后就返回，不再往下走
        when(paymentNotifyEventService.tryRecord(any())).thenReturn(false);
        String signed = "channel=" + channel + "&orderSn=ORD-3002&tradeNo=T3002&status=" + signedStatus
                + "&amount=12.30&currency=CNY";

        // 金额故意写成 12.3：mall-payment 签的是 toPlainString()，请求里是 BigDecimal，按数值比
        PaymentNotifyResult result = service.handleNotify(
                notify(channel, "ORD-3002", "T3002", providerStatus, "12.3", "CNY", signed));

        assertThat(result.message()).isEqualTo("duplicate notify event");
    }
}

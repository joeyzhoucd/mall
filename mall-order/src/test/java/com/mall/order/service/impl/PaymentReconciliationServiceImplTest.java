package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.order.client.PaymentGatewayClient;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.entity.PaymentReconciliationResultEntity;
import com.mall.order.entity.RefundInfoEntity;
import com.mall.order.service.PaymentInfoService;
import com.mall.order.service.PaymentReconciliationResultService;
import com.mall.order.service.RefundInfoService;
import com.mall.order.vo.pay.PaymentReconciliationSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationServiceImplTest {

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    @Mock
    private PaymentInfoService paymentInfoService;

    @Mock
    private RefundInfoService refundInfoService;

    @Mock
    private PaymentReconciliationResultService resultService;

    private PaymentReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentReconciliationServiceImpl(
                paymentGatewayClient,
                paymentInfoService,
                refundInfoService,
                resultService
        );
    }

    @Test
    void reconcilesMatchedPaymentAndRefundRows() {
        LocalDate date = LocalDate.of(2026, 9, 3);
        when(paymentGatewayClient.downloadReconciliationFile(date)).thenReturn("""
                row_type,reconcile_date,channel,order_sn,trade_no,refund_sn,amount,currency,status,happened_at
                PAYMENT,2026-09-03,wechat,ORD-3001,WXORD3001,,88.80,CNY,success,2026-09-03T01:00:00Z
                REFUND,2026-09-03,wechat,ORD-3001,WXORD3001,RF-3001,8.80,CNY,refunded,2026-09-03T02:00:00Z
                """);

        PaymentInfoEntity payment = new PaymentInfoEntity();
        payment.setOrderSn("ORD-3001");
        payment.setPaymentChannel("wechat");
        payment.setAlipayTradeNo("WXORD3001");
        payment.setTotalAmount(new BigDecimal("88.80"));
        payment.setPaymentCurrency("CNY");
        payment.setPaymentStatus("success");
        when(paymentInfoService.getOne(any(Wrapper.class))).thenReturn(payment);

        RefundInfoEntity refund = new RefundInfoEntity();
        refund.setOrderSn("ORD-3001");
        refund.setPaymentChannel("wechat");
        refund.setTradeNo("WXORD3001");
        refund.setRefundSn("RF-3001");
        refund.setRefund(new BigDecimal("8.80"));
        refund.setCurrency("CNY");
        refund.setRefundStatus(1);
        when(refundInfoService.getOne(any(Wrapper.class))).thenReturn(refund);

        PaymentReconciliationSummary summary = service.reconcile(date);

        assertThat(summary.totalRows()).isEqualTo(2);
        assertThat(summary.matchedRows()).isEqualTo(2);
        assertThat(summary.differentRows()).isZero();

        ArgumentCaptor<PaymentReconciliationResultEntity> captor =
                ArgumentCaptor.forClass(PaymentReconciliationResultEntity.class);
        verify(resultService, org.mockito.Mockito.times(2)).saveOrUpdateResult(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PaymentReconciliationResultEntity::getDifferenceType)
                .containsExactly("MATCH", "MATCH");
        assertThat(captor.getAllValues())
                .extracting(PaymentReconciliationResultEntity::getProcessStatus)
                .containsExactly("resolved", "resolved");
    }

    @Test
    void recordsLocalMissingDifference() {
        LocalDate date = LocalDate.of(2026, 9, 3);
        when(paymentGatewayClient.downloadReconciliationFile(date)).thenReturn("""
                row_type,reconcile_date,channel,order_sn,trade_no,refund_sn,amount,currency,status,happened_at
                PAYMENT,2026-09-03,alipay,ORD-3002,ALIORD3002,,20.00,CNY,success,2026-09-03T01:00:00Z
                """);

        PaymentReconciliationSummary summary = service.reconcile(date);

        assertThat(summary.totalRows()).isEqualTo(1);
        assertThat(summary.matchedRows()).isZero();
        assertThat(summary.differentRows()).isEqualTo(1);

        ArgumentCaptor<PaymentReconciliationResultEntity> captor =
                ArgumentCaptor.forClass(PaymentReconciliationResultEntity.class);
        verify(resultService).saveOrUpdateResult(captor.capture());
        assertThat(captor.getValue().getDifferenceType()).isEqualTo("LOCAL_MISSING");
        assertThat(captor.getValue().getProcessStatus()).isEqualTo("pending");
    }
}

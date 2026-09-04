package com.mall.payment.service;

import com.mall.payment.config.PaymentMockProperties;
import com.mall.payment.model.PaymentChannel;
import com.mall.payment.model.PaymentRequest;
import com.mall.payment.model.PaymentResponse;
import com.mall.payment.model.PaymentStatus;
import com.mall.payment.model.RefundRequest;
import com.mall.payment.model.RefundResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentMockServiceTest {

    private final PaymentMockService service = new PaymentMockService(new PaymentMockProperties(
            "test-sign-key",
            "ali-app",
            "wx-app",
            "wx-mch",
            "http://payment.test"
    ));

    @Test
    void createsAlipayPayloadWithStableIdempotentResponse() {
        PaymentRequest request = new PaymentRequest(
                PaymentChannel.ALIPAY,
                "ORD-1001",
                new BigDecimal("88.8"),
                null,
                "order subject",
                null,
                null,
                null,
                null,
                null
        );

        PaymentResponse first = service.createPayment(request);
        PaymentResponse second = service.createPayment(request);

        assertThat(first.idempotent()).isFalse();
        assertThat(second.idempotent()).isTrue();
        assertThat(second.tradeNo()).isEqualTo(first.tradeNo());
        assertThat(second.amount()).isEqualByComparingTo("88.80");
        assertThat(second.sign()).isNotBlank();
        assertThat(second.providerPayload()).containsKey("alipay_trade_precreate_response");

        @SuppressWarnings("unchecked")
        Map<String, Object> alipay = (Map<String, Object>) second.providerPayload().get("alipay_trade_precreate_response");
        assertThat(alipay)
                .containsEntry("out_trade_no", "ORD-1001")
                .containsEntry("trade_status", "WAIT_BUYER_PAY")
                .containsEntry("total_amount", "88.80");
    }

    @Test
    void exposesWechatSuccessNotificationPayload() {
        service.createPayment(new PaymentRequest(
                PaymentChannel.WECHAT,
                "ORD-1002",
                new BigDecimal("12.34"),
                "CNY",
                "wechat order",
                null,
                null,
                null,
                null,
                null
        ));

        PaymentResponse paid = service.transition(PaymentChannel.WECHAT, "ORD-1002", PaymentStatus.SUCCESS);
        Map<String, Object> notify = service.buildNotifyPayload(paid);

        assertThat(paid.providerPayload())
                .containsEntry("trade_state", "SUCCESS")
                .containsKey("code_url");
        assertThat(notify)
                .containsEntry("channel", "wechat")
                .containsEntry("tradeStatus", "SUCCESS")
                .containsKey("sign");
    }

    @Test
    void refundsSuccessfulCreditCardPayment() {
        service.createPayment(new PaymentRequest(
                PaymentChannel.CREDIT_CARD,
                "ORD-1003",
                new BigDecimal("50.00"),
                "USD",
                "card order",
                null,
                null,
                null,
                null,
                "tok_test"
        ));
        service.transition(PaymentChannel.CREDIT_CARD, "ORD-1003", PaymentStatus.SUCCESS);

        RefundResponse refund = service.refund(new RefundRequest(
                PaymentChannel.CREDIT_CARD,
                "ORD-1003",
                null,
                "RF-1003",
                new BigDecimal("10.00"),
                "test"
        ));

        assertThat(refund.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refund.providerPayload())
                .containsEntry("object", "refund")
                .containsEntry("status", "succeeded");
    }

    @Test
    void rejectsConflictingIdempotentPayment() {
        service.createPayment(new PaymentRequest(
                PaymentChannel.ALIPAY,
                "ORD-1004",
                new BigDecimal("10.00"),
                "CNY",
                "order",
                null,
                null,
                null,
                null,
                null
        ));

        assertThatThrownBy(() -> service.createPayment(new PaymentRequest(
                PaymentChannel.ALIPAY,
                "ORD-1004",
                new BigDecimal("11.00"),
                "CNY",
                "order",
                null,
                null,
                null,
                null,
                null
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conflicts");
    }

    @Test
    void exportsPaymentAndRefundReconciliationCsv() {
        service.createPayment(new PaymentRequest(
                PaymentChannel.WECHAT,
                "ORD-1005",
                new BigDecimal("33.30"),
                "CNY",
                "order",
                null,
                null,
                null,
                null,
                null
        ));
        service.transition(PaymentChannel.WECHAT, "ORD-1005", PaymentStatus.SUCCESS);
        service.refund(new RefundRequest(
                PaymentChannel.WECHAT,
                "ORD-1005",
                null,
                "RF-1005",
                new BigDecimal("3.30"),
                "partial refund"
        ));

        String csv = service.exportReconciliationCsv(LocalDate.now(ZoneOffset.UTC));

        assertThat(csv).startsWith("row_type,reconcile_date,channel,order_sn,trade_no,refund_sn,amount,currency,status,happened_at");
        assertThat(csv).contains("PAYMENT,");
        assertThat(csv).contains(",wechat,ORD-1005,WXORD1005,,33.30,CNY,refunded,");
        assertThat(csv).contains("REFUND,");
        assertThat(csv).contains(",wechat,ORD-1005,WXORD1005,RF-1005,3.30,CNY,refunded,");
    }
}

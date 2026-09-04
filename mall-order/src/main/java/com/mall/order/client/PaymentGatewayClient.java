package com.mall.order.client;

import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.order.vo.pay.PaymentGatewayRequest;
import com.mall.order.vo.pay.PaymentGatewayResponse;
import com.mall.order.vo.pay.PaymentRefundGatewayRequest;
import com.mall.order.vo.pay.PaymentRefundGatewayResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentGatewayClient {

    private final RestClient paymentGatewayRestClient;
    private final ObjectMapper objectMapper;

    public PaymentGatewayClient(RestClient paymentGatewayRestClient, ObjectMapper objectMapper) {
        this.paymentGatewayRestClient = paymentGatewayRestClient;
        this.objectMapper = objectMapper;
    }

    public PaymentGatewayResponse createPayment(PaymentGatewayRequest request) {
        R response = paymentGatewayRestClient.post()
                .uri("/payment/mock/payments")
                .body(request)
                .retrieve()
                .body(R.class);
        PaymentGatewayResponse payment = RUtils.getData(
                response,
                "payment",
                objectMapper,
                new TypeReference<PaymentGatewayResponse>() {}
        );
        if (payment == null) {
            throw new IllegalStateException("payment gateway returned empty payment");
        }
        return payment;
    }

    public PaymentGatewayResponse queryPayment(String channel, String orderSn) {
        R response = paymentGatewayRestClient.get()
                .uri("/payment/mock/payments/{channel}/{orderSn}", channel, orderSn)
                .retrieve()
                .body(R.class);
        PaymentGatewayResponse payment = RUtils.getData(
                response,
                "payment",
                objectMapper,
                new TypeReference<PaymentGatewayResponse>() {}
        );
        if (payment == null) {
            throw new IllegalStateException("payment gateway returned empty payment");
        }
        return payment;
    }

    public PaymentRefundGatewayResponse refund(PaymentRefundGatewayRequest request) {
        R response = paymentGatewayRestClient.post()
                .uri("/payment/mock/refunds")
                .body(request)
                .retrieve()
                .body(R.class);
        PaymentRefundGatewayResponse refund = RUtils.getData(
                response,
                "refund",
                objectMapper,
                new TypeReference<PaymentRefundGatewayResponse>() {}
        );
        if (refund == null) {
            throw new IllegalStateException("payment gateway returned empty refund");
        }
        return refund;
    }

    public String downloadReconciliationFile(java.time.LocalDate date) {
        return paymentGatewayRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/payment/mock/reconciliation")
                        .queryParam("date", date)
                        .build())
                .retrieve()
                .body(String.class);
    }
}

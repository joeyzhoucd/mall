package com.mall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.payment.gateway")
public record PaymentGatewayProperties(
        String baseUrl,
        String notifyUrl,
        String returnUrl,
        String signKey
) {

    public PaymentGatewayProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:9010";
        }
        if (notifyUrl == null || notifyUrl.isBlank()) {
            notifyUrl = "http://order.mall.com/order/payments/notify";
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            returnUrl = "http://order.mall.com/order/payment.html";
        }
        if (signKey == null || signKey.isBlank()) {
            signKey = "mall-payment-local-mock-sign-key";
        }
    }
}

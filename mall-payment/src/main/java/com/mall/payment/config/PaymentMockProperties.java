package com.mall.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.payment.mock")
public record PaymentMockProperties(
        String signKey,
        String alipayAppId,
        String wechatAppId,
        String wechatMchId,
        String gatewayBaseUrl
) {

    public PaymentMockProperties {
        if (signKey == null || signKey.isBlank()) {
            signKey = "mall-payment-local-mock-sign-key";
        }
        if (alipayAppId == null || alipayAppId.isBlank()) {
            alipayAppId = "2026090300000000";
        }
        if (wechatAppId == null || wechatAppId.isBlank()) {
            wechatAppId = "wx0000000000000000";
        }
        if (wechatMchId == null || wechatMchId.isBlank()) {
            wechatMchId = "1900000000";
        }
        if (gatewayBaseUrl == null || gatewayBaseUrl.isBlank()) {
            gatewayBaseUrl = "http://localhost:9010";
        }
    }
}

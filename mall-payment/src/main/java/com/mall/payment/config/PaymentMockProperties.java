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
        // 不再兜底成公开默认值（2026-10-06），原因见 mall-order 的 PaymentGatewayProperties
        // 必须检查 "${"：@ConfigurationProperties 对解析不了的占位符会原样保留字面串而不是报错（实测）
        if (signKey == null || signKey.isBlank() || signKey.contains("${")) {
            throw new IllegalStateException("mall.payment.mock.sign-key 未配置（环境变量 PAYMENT_MOCK_SIGN_KEY）");
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

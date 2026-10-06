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
        // 不再兜底成公开默认值（2026-10-06）：缺了就启动失败，而不是悄悄用一个仓库里人人可见的密钥验签。
        // 【必须检查 "${"】@ConfigurationProperties 绑定时解析不了的占位符【不报错】，而是原样留下
        // 字面串 "${PAYMENT_GATEWAY_SIGN_KEY}" —— 不为空、能过 isBlank，于是拿一个人人可推断的串当密钥。
        // 实测过（mall-payment 同款写法，不设环境变量启动，返回的签名正是用这个字面串算的）。
        // 和 @Value 不同：@Value 遇到同样情况会启动失败（mall.seckill.internal-token 那次 CI 就是这么挂的）。
        if (signKey == null || signKey.isBlank() || signKey.contains("${")) {
            throw new IllegalStateException("mall.payment.gateway.sign-key 未配置（环境变量 PAYMENT_GATEWAY_SIGN_KEY）");
        }
    }
}

package com.mall.order.vo.pay;

public record CreateOrderPaymentRequest(
        String channel,
        String currency,
        String subject,
        String description,
        String clientIp,
        String cardToken
) {
}

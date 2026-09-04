package com.mall.order.vo.pay;

import java.math.BigDecimal;

public record PaymentGatewayRequest(
        String channel,
        String orderSn,
        BigDecimal amount,
        String currency,
        String subject,
        String description,
        String notifyUrl,
        String returnUrl,
        String clientIp,
        String cardToken
) {
}

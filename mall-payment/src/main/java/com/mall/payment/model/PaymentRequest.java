package com.mall.payment.model;

import java.math.BigDecimal;

public record PaymentRequest(
        PaymentChannel channel,
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

    public PaymentRequest withChannel(PaymentChannel targetChannel) {
        return new PaymentRequest(
                targetChannel,
                orderSn,
                amount,
                currency,
                subject,
                description,
                notifyUrl,
                returnUrl,
                clientIp,
                cardToken
        );
    }
}

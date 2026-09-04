package com.mall.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record RefundResponse(
        PaymentChannel channel,
        String orderSn,
        String tradeNo,
        String refundSn,
        String refundTradeNo,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String currency,
        boolean idempotent,
        String signedContent,
        String sign,
        Map<String, Object> providerPayload,
        Instant createdAt
) {
}

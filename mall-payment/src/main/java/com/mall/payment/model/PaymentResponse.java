package com.mall.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record PaymentResponse(
        PaymentChannel channel,
        String orderSn,
        String tradeNo,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String subject,
        String payUrl,
        String qrCode,
        String prepayId,
        boolean idempotent,
        String signedContent,
        String sign,
        Map<String, Object> providerPayload,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.mall.order.vo.pay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record PaymentGatewayResponse(
        String channel,
        String orderSn,
        String tradeNo,
        String status,
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

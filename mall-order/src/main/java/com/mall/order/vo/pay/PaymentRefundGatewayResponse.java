package com.mall.order.vo.pay;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record PaymentRefundGatewayResponse(
        String channel,
        String orderSn,
        String tradeNo,
        String refundSn,
        String refundTradeNo,
        String paymentStatus,
        BigDecimal amount,
        String currency,
        boolean idempotent,
        String signedContent,
        String sign,
        Map<String, Object> providerPayload,
        Instant createdAt
) {
}

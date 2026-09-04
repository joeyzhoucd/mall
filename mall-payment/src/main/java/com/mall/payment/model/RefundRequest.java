package com.mall.payment.model;

import java.math.BigDecimal;

public record RefundRequest(
        PaymentChannel channel,
        String orderSn,
        String tradeNo,
        String refundSn,
        BigDecimal amount,
        String reason
) {
}

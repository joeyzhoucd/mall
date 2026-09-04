package com.mall.order.vo.pay;

import java.math.BigDecimal;

public record RefundOrderPaymentRequest(
        String channel,
        String orderSn,
        String tradeNo,
        String refundSn,
        BigDecimal amount,
        String reason
) {
}

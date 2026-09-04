package com.mall.order.vo.pay;

import java.math.BigDecimal;

public record PaymentRefundGatewayRequest(
        String channel,
        String orderSn,
        String tradeNo,
        String refundSn,
        BigDecimal amount,
        String reason
) {
}

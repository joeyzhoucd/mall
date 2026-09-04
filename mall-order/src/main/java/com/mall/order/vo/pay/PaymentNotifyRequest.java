package com.mall.order.vo.pay;

import java.math.BigDecimal;

public record PaymentNotifyRequest(
        String channel,
        String orderSn,
        String tradeNo,
        String tradeStatus,
        BigDecimal totalAmount,
        String currency,
        String notifyTime,
        String signedContent,
        String sign
) {
}

package com.mall.order.vo.pay;

public record PaymentNotifyResult(
        boolean accepted,
        boolean idempotent,
        String status,
        String message
) {
}

package com.mall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.payment.reconciliation")
public record PaymentReconciliationProperties(
        Boolean enabled,
        long initialDelayMs,
        long fixedDelayMs,
        int staleAfterSeconds,
        int batchSize
) {

    public PaymentReconciliationProperties {
        if (enabled == null) {
            enabled = true;
        }
        if (initialDelayMs <= 0) {
            initialDelayMs = 30_000;
        }
        if (fixedDelayMs <= 0) {
            fixedDelayMs = 60_000;
        }
        if (staleAfterSeconds <= 0) {
            staleAfterSeconds = 30;
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        batchSize = Math.min(batchSize, 500);
    }
}

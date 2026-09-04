package com.mall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.order.outbox")
public record OrderOutboxProperties(
        boolean enabled,
        long initialDelayMs,
        long fixedDelayMs,
        int batchSize,
        int maxAttempts,
        long retryDelayMs,
        long sendingTimeoutMs
) {
    public OrderOutboxProperties {
        if (initialDelayMs <= 0) {
            initialDelayMs = 10_000;
        }
        if (fixedDelayMs <= 0) {
            fixedDelayMs = 5_000;
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }
        if (retryDelayMs <= 0) {
            retryDelayMs = 5_000;
        }
        if (sendingTimeoutMs <= 0) {
            sendingTimeoutMs = 60_000;
        }
    }
}

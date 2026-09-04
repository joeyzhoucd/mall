package com.mall.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mall.payment.statement-reconciliation")
public record PaymentStatementReconciliationProperties(
        Boolean enabled,
        String cron,
        String zone
) {

    public PaymentStatementReconciliationProperties {
        if (enabled == null) {
            enabled = false;
        }
        if (cron == null || cron.isBlank()) {
            cron = "0 30 1 * * *";
        }
        if (zone == null || zone.isBlank()) {
            zone = "UTC";
        }
    }
}

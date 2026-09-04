package com.mall.order.schedule;

import com.mall.order.config.PaymentReconciliationProperties;
import com.mall.order.service.OrderPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

@Component
@ConditionalOnProperty(name = "mall.payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationTask.class);

    private final OrderPaymentService orderPaymentService;
    private final PaymentReconciliationProperties properties;

    public PaymentReconciliationTask(OrderPaymentService orderPaymentService,
                                     PaymentReconciliationProperties properties) {
        this.orderPaymentService = orderPaymentService;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${mall.payment.reconciliation.fixed-delay-ms:60000}",
            initialDelayString = "${mall.payment.reconciliation.initial-delay-ms:30000}"
    )
    public void reconcilePendingPayments() {
        Date createdBefore = new Date(System.currentTimeMillis()
                - Duration.ofSeconds(properties.staleAfterSeconds()).toMillis());
        int reconciled = orderPaymentService.reconcilePendingPayments(createdBefore, properties.batchSize());
        if (reconciled > 0) {
            log.info("支付主动查单补偿完成: reconciled={}", reconciled);
        }
    }
}

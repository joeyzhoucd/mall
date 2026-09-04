package com.mall.order.schedule;

import com.mall.order.config.PaymentStatementReconciliationProperties;
import com.mall.order.service.PaymentReconciliationService;
import com.mall.order.vo.pay.PaymentReconciliationSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@ConditionalOnProperty(name = "mall.payment.statement-reconciliation.enabled", havingValue = "true")
public class PaymentStatementReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatementReconciliationTask.class);

    private final PaymentReconciliationService paymentReconciliationService;
    private final PaymentStatementReconciliationProperties properties;

    public PaymentStatementReconciliationTask(PaymentReconciliationService paymentReconciliationService,
                                              PaymentStatementReconciliationProperties properties) {
        this.paymentReconciliationService = paymentReconciliationService;
        this.properties = properties;
    }

    @Scheduled(
            cron = "${mall.payment.statement-reconciliation.cron:0 30 1 * * *}",
            zone = "${mall.payment.statement-reconciliation.zone:UTC}"
    )
    public void reconcileYesterday() {
        LocalDate date = LocalDate.now(ZoneId.of(properties.zone())).minusDays(1);
        PaymentReconciliationSummary summary = paymentReconciliationService.reconcile(date);
        log.info("支付文件对账完成: date={} total={} matched={} different={}",
                summary.reconcileDate(), summary.totalRows(), summary.matchedRows(), summary.differentRows());
    }
}

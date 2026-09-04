package com.mall.order.vo.pay;

import java.time.LocalDate;

public record PaymentReconciliationSummary(
        LocalDate reconcileDate,
        int totalRows,
        int matchedRows,
        int differentRows
) {
}

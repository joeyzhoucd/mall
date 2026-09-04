package com.mall.order.service;

import com.mall.order.vo.pay.PaymentReconciliationSummary;

import java.time.LocalDate;

public interface PaymentReconciliationService {

    PaymentReconciliationSummary reconcile(LocalDate date);
}

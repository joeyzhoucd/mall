package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.order.entity.PaymentReconciliationResultEntity;

public interface PaymentReconciliationResultService extends IService<PaymentReconciliationResultEntity> {

    void saveOrUpdateResult(PaymentReconciliationResultEntity result);
}

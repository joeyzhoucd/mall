package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.order.dao.PaymentReconciliationResultDao;
import com.mall.order.entity.PaymentReconciliationResultEntity;
import com.mall.order.service.PaymentReconciliationResultService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service("paymentReconciliationResultService")
public class PaymentReconciliationResultServiceImpl
        extends ServiceImpl<PaymentReconciliationResultDao, PaymentReconciliationResultEntity>
        implements PaymentReconciliationResultService {

    @Override
    public void saveOrUpdateResult(PaymentReconciliationResultEntity result) {
        if (result == null || result.getReconcileDate() == null || StringUtils.isBlank(result.getRowType())) {
            throw new IllegalArgumentException("reconciliation result natural key is required");
        }
        Date now = new Date();
        QueryWrapper<PaymentReconciliationResultEntity> wrapper = new QueryWrapper<PaymentReconciliationResultEntity>()
                .eq("reconcile_date", result.getReconcileDate())
                .eq("row_type", result.getRowType())
                .eq("channel", result.getChannel())
                .eq("order_sn", result.getOrderSn())
                .eq("trade_no", result.getTradeNo());
        if (StringUtils.isBlank(result.getRefundSn())) {
            wrapper.isNull("refund_sn");
        } else {
            wrapper.eq("refund_sn", result.getRefundSn());
        }

        PaymentReconciliationResultEntity existing = this.getOne(wrapper);
        result.setUpdateTime(now);
        if (existing == null) {
            result.setCreateTime(now);
            this.save(result);
        } else {
            result.setId(existing.getId());
            result.setCreateTime(existing.getCreateTime());
            this.updateById(result);
        }
    }
}

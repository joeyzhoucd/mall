package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.order.dao.PaymentInfoDao;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.service.PaymentInfoService;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;


@Service("paymentInfoService")
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoDao, PaymentInfoEntity> implements PaymentInfoService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<PaymentInfoEntity> page = this.page(
                new Query<PaymentInfoEntity>().getPage(params),
                new QueryWrapper<PaymentInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<PaymentInfoEntity> listPendingPaymentsForReconciliation(Date createdBefore, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 500));
        QueryWrapper<PaymentInfoEntity> wrapper = new QueryWrapper<PaymentInfoEntity>()
                .eq("payment_status", "pending")
                .isNotNull("payment_channel")
                .ne("payment_channel", "")
                .orderByAsc("create_time")
                .last("LIMIT " + boundedLimit);
        if (createdBefore != null) {
            wrapper.le("create_time", createdBefore);
        }
        return this.list(wrapper);
    }

}

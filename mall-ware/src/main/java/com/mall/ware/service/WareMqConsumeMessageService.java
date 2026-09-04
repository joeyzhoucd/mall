package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.ware.entity.WareMqConsumeMessageEntity;

public interface WareMqConsumeMessageService extends IService<WareMqConsumeMessageEntity> {

    boolean consumeOnce(String consumerGroup, String messageKey, String businessType, Runnable handler);
}

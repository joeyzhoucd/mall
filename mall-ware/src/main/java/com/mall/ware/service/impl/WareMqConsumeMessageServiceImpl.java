package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.constant.MqConsumeStatus;
import com.mall.ware.dao.WareMqConsumeMessageDao;
import com.mall.ware.entity.WareMqConsumeMessageEntity;
import com.mall.ware.service.WareMqConsumeMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service("wareMqConsumeMessageService")
public class WareMqConsumeMessageServiceImpl
        extends ServiceImpl<WareMqConsumeMessageDao, WareMqConsumeMessageEntity>
        implements WareMqConsumeMessageService {

    private static final int LAST_ERROR_LIMIT = 500;

    @Override
    public boolean consumeOnce(String consumerGroup, String messageKey, String businessType, Runnable handler) {
        if (StringUtils.isAnyBlank(consumerGroup, messageKey, businessType) || handler == null) {
            throw new IllegalArgumentException("consumer group, message key, business type and handler are required");
        }
        if (!claim(consumerGroup, messageKey, businessType)) {
            return false;
        }
        try {
            handler.run();
            markSuccess(consumerGroup, messageKey);
            return true;
        } catch (RuntimeException e) {
            markFailed(consumerGroup, messageKey, e.getMessage());
            throw e;
        } catch (Error e) {
            markFailed(consumerGroup, messageKey, e.getMessage());
            throw e;
        }
    }

    private boolean claim(String consumerGroup, String messageKey, String businessType) {
        Date now = new Date();
        WareMqConsumeMessageEntity entity = new WareMqConsumeMessageEntity();
        entity.setConsumerGroup(consumerGroup);
        entity.setMessageKey(messageKey);
        entity.setBusinessType(businessType);
        entity.setStatus(MqConsumeStatus.PROCESSING);
        entity.setConsumeCount(1);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        try {
            return this.save(entity);
        } catch (DuplicateKeyException ignored) {
            WareMqConsumeMessageEntity existing = this.getOne(new QueryWrapper<WareMqConsumeMessageEntity>()
                    .eq("consumer_group", consumerGroup)
                    .eq("message_key", messageKey));
            if (existing == null || existing.getStatus() != null && existing.getStatus() == MqConsumeStatus.SUCCESS) {
                return false;
            }
            return this.update(new UpdateWrapper<WareMqConsumeMessageEntity>()
                    .set("status", MqConsumeStatus.PROCESSING)
                    .set("business_type", businessType)
                    .set("last_error", null)
                    .set("update_time", now)
                    .setSql("consume_count = consume_count + 1")
                    .eq("id", existing.getId())
                    .in("status", List.of(MqConsumeStatus.PROCESSING, MqConsumeStatus.FAILED)));
        }
    }

    private void markSuccess(String consumerGroup, String messageKey) {
        Date now = new Date();
        this.update(new UpdateWrapper<WareMqConsumeMessageEntity>()
                .set("status", MqConsumeStatus.SUCCESS)
                .set("last_error", null)
                .set("success_time", now)
                .set("update_time", now)
                .eq("consumer_group", consumerGroup)
                .eq("message_key", messageKey));
    }

    private void markFailed(String consumerGroup, String messageKey, String reason) {
        this.update(new UpdateWrapper<WareMqConsumeMessageEntity>()
                .set("status", MqConsumeStatus.FAILED)
                .set("last_error", truncate(reason))
                .set("update_time", new Date())
                .eq("consumer_group", consumerGroup)
                .eq("message_key", messageKey)
                .ne("status", MqConsumeStatus.SUCCESS));
    }

    private String truncate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.length() <= LAST_ERROR_LIMIT ? value : value.substring(0, LAST_ERROR_LIMIT);
    }
}

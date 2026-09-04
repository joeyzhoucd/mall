package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.constant.OutboxMessageStatus;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.config.StockOutboxProperties;
import com.mall.ware.dao.StockOutboxMessageDao;
import com.mall.ware.entity.StockOutboxMessageEntity;
import com.mall.ware.service.StockOutboxMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service("stockOutboxMessageService")
public class StockOutboxMessageServiceImpl extends ServiceImpl<StockOutboxMessageDao, StockOutboxMessageEntity>
        implements StockOutboxMessageService {

    private static final int LAST_ERROR_LIMIT = 500;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final StockOutboxProperties properties;

    public StockOutboxMessageServiceImpl(RabbitTemplate rabbitTemplate,
                                         ObjectMapper objectMapper,
                                         StockOutboxProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<StockOutboxMessageEntity> page = this.page(
                new Query<StockOutboxMessageEntity>().getPage(params),
                new QueryWrapper<StockOutboxMessageEntity>().orderByDesc("id")
        );
        return new PageUtils(page);
    }

    @Override
    public StockOutboxMessageEntity enqueue(String messageKey,
                                            String businessType,
                                            String businessKey,
                                            String exchange,
                                            String routingKey,
                                            Object payload) {
        if (StringUtils.isAnyBlank(messageKey, businessType, businessKey, exchange, routingKey) || payload == null) {
            throw new IllegalArgumentException("outbox message key, business key and payload are required");
        }
        StockOutboxMessageEntity existing = this.getOne(new QueryWrapper<StockOutboxMessageEntity>()
                .eq("message_key", messageKey));
        if (existing != null) {
            publishAfterCommit(existing.getId());
            return existing;
        }

        Date now = new Date();
        StockOutboxMessageEntity entity = new StockOutboxMessageEntity();
        entity.setMessageKey(messageKey);
        entity.setBusinessType(businessType);
        entity.setBusinessKey(businessKey);
        entity.setExchangeName(exchange);
        entity.setRoutingKey(routingKey);
        entity.setPayloadType(payload.getClass().getName());
        entity.setPayload(toJson(payload));
        entity.setStatus(OutboxMessageStatus.PENDING);
        entity.setRetryCount(0);
        entity.setNextRetryTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        this.save(entity);
        publishAfterCommit(entity.getId());
        return entity;
    }

    @Override
    public int publishReadyMessages() {
        if (!properties.enabled()) {
            return 0;
        }
        Date now = new Date();
        recoverStaleSending(now);
        List<StockOutboxMessageEntity> messages = this.list(new QueryWrapper<StockOutboxMessageEntity>()
                .in("status", OutboxMessageStatus.PENDING, OutboxMessageStatus.FAILED)
                .lt("retry_count", properties.maxAttempts())
                .and(w -> w.isNull("next_retry_time").or().le("next_retry_time", now))
                .orderByAsc("next_retry_time")
                .orderByAsc("id")
                .last("LIMIT " + properties.batchSize()));
        int published = 0;
        for (StockOutboxMessageEntity message : messages) {
            if (message != null && publish(message.getId(), false)) {
                published++;
            }
        }
        markRetryExhaustedDead();
        return published;
    }

    @Override
    public boolean resend(Long id) {
        return publish(id, true);
    }

    @Override
    public boolean markDead(Long id, String reason) {
        if (id == null) {
            return false;
        }
        StockOutboxMessageEntity update = new StockOutboxMessageEntity();
        update.setStatus(OutboxMessageStatus.DEAD);
        update.setLastError(truncate(StringUtils.defaultIfBlank(reason, "manually marked dead")));
        update.setUpdateTime(new Date());
        return this.update(update, new UpdateWrapper<StockOutboxMessageEntity>()
                .eq("id", id)
                .ne("status", OutboxMessageStatus.SENT));
    }

    @Override
    public void markSent(Long id) {
        if (id == null) {
            return;
        }
        Date now = new Date();
        this.update(new UpdateWrapper<StockOutboxMessageEntity>()
                .set("status", OutboxMessageStatus.SENT)
                .set("last_error", null)
                .set("sent_time", now)
                .set("update_time", now)
                .eq("id", id)
                .eq("status", OutboxMessageStatus.SENDING));
    }

    @Override
    public void markFailed(Long id, String reason) {
        if (id == null) {
            return;
        }
        StockOutboxMessageEntity current = this.getById(id);
        if (current == null || current.getStatus() != null && current.getStatus() == OutboxMessageStatus.SENT) {
            return;
        }
        int retryCount = (current.getRetryCount() == null ? 0 : current.getRetryCount()) + 1;
        Date now = new Date();
        StockOutboxMessageEntity update = new StockOutboxMessageEntity();
        update.setStatus(retryCount >= properties.maxAttempts() ? OutboxMessageStatus.DEAD : OutboxMessageStatus.FAILED);
        update.setRetryCount(retryCount);
        update.setNextRetryTime(new Date(now.getTime() + properties.retryDelayMs()));
        update.setLastError(truncate(reason));
        update.setUpdateTime(now);
        this.update(update, new UpdateWrapper<StockOutboxMessageEntity>()
                .eq("id", id)
                .ne("status", OutboxMessageStatus.SENT));
    }

    private void publishAfterCommit(Long id) {
        if (!properties.enabled() || id == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(id, false);
                }
            });
        } else {
            publish(id, false);
        }
    }

    private boolean publish(Long id, boolean forceDead) {
        if (!properties.enabled() || id == null) {
            return false;
        }
        StockOutboxMessageEntity current = this.getById(id);
        if (current == null || current.getStatus() == null || current.getStatus() == OutboxMessageStatus.SENT) {
            return false;
        }
        if (!forceDead && !OutboxMessageStatus.canDispatch(current.getStatus())) {
            return false;
        }
        if (!forceDead && current.getRetryCount() != null && current.getRetryCount() >= properties.maxAttempts()) {
            markDead(id, "retry attempts exhausted");
            return false;
        }

        Date now = new Date();
        StockOutboxMessageEntity sending = new StockOutboxMessageEntity();
        sending.setStatus(OutboxMessageStatus.SENDING);
        sending.setUpdateTime(now);
        boolean claimed = this.update(sending, new UpdateWrapper<StockOutboxMessageEntity>()
                .eq("id", id)
                .in("status", forceDead
                        ? List.of(OutboxMessageStatus.PENDING, OutboxMessageStatus.FAILED, OutboxMessageStatus.DEAD)
                        : List.of(OutboxMessageStatus.PENDING, OutboxMessageStatus.FAILED)));
        if (!claimed) {
            return false;
        }

        StockOutboxMessageEntity message = this.getById(id);
        if (message == null) {
            return false;
        }
        try {
            Object payload = fromJson(message.getPayload(), message.getPayloadType());
            rabbitTemplate.convertAndSend(
                    message.getExchangeName(),
                    message.getRoutingKey(),
                    payload,
                    rabbitMessage -> {
                        rabbitMessage.getMessageProperties().setCorrelationId(String.valueOf(id));
                        return rabbitMessage;
                    },
                    new CorrelationData(String.valueOf(id)));
            return true;
        } catch (RuntimeException e) {
            markFailed(id, e.getMessage());
            return false;
        }
    }

    private void markRetryExhaustedDead() {
        StockOutboxMessageEntity update = new StockOutboxMessageEntity();
        update.setStatus(OutboxMessageStatus.DEAD);
        update.setLastError("retry attempts exhausted");
        update.setUpdateTime(new Date());
        this.update(update, new UpdateWrapper<StockOutboxMessageEntity>()
                .in("status", OutboxMessageStatus.PENDING, OutboxMessageStatus.FAILED)
                .ge("retry_count", properties.maxAttempts()));
    }

    private void recoverStaleSending(Date now) {
        StockOutboxMessageEntity update = new StockOutboxMessageEntity();
        update.setStatus(OutboxMessageStatus.FAILED);
        update.setNextRetryTime(now);
        update.setLastError("sending timeout before broker confirm");
        update.setUpdateTime(now);
        this.update(update, new UpdateWrapper<StockOutboxMessageEntity>()
                .eq("status", OutboxMessageStatus.SENDING)
                .lt("update_time", new Date(now.getTime() - properties.sendingTimeoutMs())));
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("outbox payload cannot be serialized", e);
        }
    }

    private Object fromJson(String payload, String payloadType) {
        try {
            return objectMapper.readValue(payload, Class.forName(payloadType));
        } catch (Exception e) {
            throw new IllegalStateException("outbox payload cannot be deserialized", e);
        }
    }

    private String truncate(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.length() <= LAST_ERROR_LIMIT ? value : value.substring(0, LAST_ERROR_LIMIT);
    }
}

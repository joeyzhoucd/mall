package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.constant.OrderOutboxStatus;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.order.config.OrderOutboxProperties;
import com.mall.order.dao.OrderOutboxMessageDao;
import com.mall.order.entity.OrderOutboxMessageEntity;
import com.mall.order.service.OrderOutboxMessageService;
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

@Service("orderOutboxMessageService")
public class OrderOutboxMessageServiceImpl extends ServiceImpl<OrderOutboxMessageDao, OrderOutboxMessageEntity>
        implements OrderOutboxMessageService {

    private static final int LAST_ERROR_LIMIT = 500;

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OrderOutboxProperties properties;

    public OrderOutboxMessageServiceImpl(RabbitTemplate rabbitTemplate,
                                         ObjectMapper objectMapper,
                                         OrderOutboxProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderOutboxMessageEntity> page = this.page(
                new Query<OrderOutboxMessageEntity>().getPage(params),
                new QueryWrapper<OrderOutboxMessageEntity>().orderByDesc("id")
        );
        return new PageUtils(page);
    }

    @Override
    public OrderOutboxMessageEntity enqueue(String messageKey,
                                            String businessType,
                                            String businessKey,
                                            String exchange,
                                            String routingKey,
                                            Object payload) {
        if (StringUtils.isAnyBlank(messageKey, businessType, businessKey, exchange, routingKey) || payload == null) {
            throw new IllegalArgumentException("outbox message key, business key and payload are required");
        }
        OrderOutboxMessageEntity existing = this.getOne(new QueryWrapper<OrderOutboxMessageEntity>()
                .eq("message_key", messageKey));
        if (existing != null) {
            publishAfterCommit(existing.getId());
            return existing;
        }

        Date now = new Date();
        OrderOutboxMessageEntity entity = new OrderOutboxMessageEntity();
        entity.setMessageKey(messageKey);
        entity.setBusinessType(businessType);
        entity.setBusinessKey(businessKey);
        entity.setExchangeName(exchange);
        entity.setRoutingKey(routingKey);
        entity.setPayloadType(payload.getClass().getName());
        entity.setPayload(toJson(payload));
        entity.setStatus(OrderOutboxStatus.PENDING);
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
        List<OrderOutboxMessageEntity> messages = this.list(new QueryWrapper<OrderOutboxMessageEntity>()
                .in("status", OrderOutboxStatus.PENDING, OrderOutboxStatus.FAILED)
                .lt("retry_count", properties.maxAttempts())
                .and(w -> w.isNull("next_retry_time").or().le("next_retry_time", now))
                .orderByAsc("next_retry_time")
                .orderByAsc("id")
                .last("LIMIT " + properties.batchSize()));
        int published = 0;
        for (OrderOutboxMessageEntity message : messages) {
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
        OrderOutboxMessageEntity update = new OrderOutboxMessageEntity();
        update.setStatus(OrderOutboxStatus.DEAD);
        update.setLastError(truncate(StringUtils.defaultIfBlank(reason, "manually marked dead")));
        update.setUpdateTime(new Date());
        return this.update(update, new UpdateWrapper<OrderOutboxMessageEntity>()
                .eq("id", id)
                .ne("status", OrderOutboxStatus.SENT));
    }

    @Override
    public void markSent(Long id) {
        if (id == null) {
            return;
        }
        Date now = new Date();
        this.update(new UpdateWrapper<OrderOutboxMessageEntity>()
                .set("status", OrderOutboxStatus.SENT)
                .set("last_error", null)
                .set("sent_time", now)
                .set("update_time", now)
                .eq("id", id)
                .eq("status", OrderOutboxStatus.SENDING));
    }

    @Override
    public void markFailed(Long id, String reason) {
        if (id == null) {
            return;
        }
        OrderOutboxMessageEntity current = this.getById(id);
        if (current == null || current.getStatus() != null && current.getStatus() == OrderOutboxStatus.SENT) {
            return;
        }
        int retryCount = (current.getRetryCount() == null ? 0 : current.getRetryCount()) + 1;
        Date now = new Date();
        OrderOutboxMessageEntity update = new OrderOutboxMessageEntity();
        update.setStatus(retryCount >= properties.maxAttempts() ? OrderOutboxStatus.DEAD : OrderOutboxStatus.FAILED);
        update.setRetryCount(retryCount);
        update.setNextRetryTime(new Date(now.getTime() + properties.retryDelayMs()));
        update.setLastError(truncate(reason));
        update.setUpdateTime(now);
        this.update(update, new UpdateWrapper<OrderOutboxMessageEntity>()
                .eq("id", id)
                .ne("status", OrderOutboxStatus.SENT));
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
        OrderOutboxMessageEntity current = this.getById(id);
        if (current == null || current.getStatus() == null || current.getStatus() == OrderOutboxStatus.SENT) {
            return false;
        }
        if (!forceDead && !OrderOutboxStatus.canDispatch(current.getStatus())) {
            return false;
        }
        if (!forceDead && current.getRetryCount() != null && current.getRetryCount() >= properties.maxAttempts()) {
            markDead(id, "retry attempts exhausted");
            return false;
        }

        Date now = new Date();
        OrderOutboxMessageEntity sending = new OrderOutboxMessageEntity();
        sending.setStatus(OrderOutboxStatus.SENDING);
        sending.setUpdateTime(now);
        boolean claimed = this.update(sending, new UpdateWrapper<OrderOutboxMessageEntity>()
                .eq("id", id)
                .in("status", forceDead
                        ? List.of(OrderOutboxStatus.PENDING, OrderOutboxStatus.FAILED, OrderOutboxStatus.DEAD)
                        : List.of(OrderOutboxStatus.PENDING, OrderOutboxStatus.FAILED)));
        if (!claimed) {
            return false;
        }

        OrderOutboxMessageEntity message = this.getById(id);
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
        OrderOutboxMessageEntity update = new OrderOutboxMessageEntity();
        update.setStatus(OrderOutboxStatus.DEAD);
        update.setLastError("retry attempts exhausted");
        update.setUpdateTime(new Date());
        this.update(update, new UpdateWrapper<OrderOutboxMessageEntity>()
                .in("status", OrderOutboxStatus.PENDING, OrderOutboxStatus.FAILED)
                .ge("retry_count", properties.maxAttempts()));
    }

    private void recoverStaleSending(Date now) {
        OrderOutboxMessageEntity update = new OrderOutboxMessageEntity();
        update.setStatus(OrderOutboxStatus.FAILED);
        update.setNextRetryTime(now);
        update.setLastError("sending timeout before broker confirm");
        update.setUpdateTime(now);
        this.update(update, new UpdateWrapper<OrderOutboxMessageEntity>()
                .eq("status", OrderOutboxStatus.SENDING)
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

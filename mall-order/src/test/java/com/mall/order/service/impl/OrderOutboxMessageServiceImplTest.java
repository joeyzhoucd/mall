package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OrderOutboxStatus;
import com.mall.common.to.OrderCloseTo;
import com.mall.order.config.OrderOutboxProperties;
import com.mall.order.entity.OrderOutboxMessageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class OrderOutboxMessageServiceImplTest {

    private RabbitTemplate rabbitTemplate;
    private OrderOutboxMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        service = spy(new OrderOutboxMessageServiceImpl(
                rabbitTemplate,
                new ObjectMapper(),
                new OrderOutboxProperties(true, 10_000, 5_000, 100, 10, 5_000, 60_000)
        ));
    }

    @Test
    void enqueuePersistsSerializablePendingMessage() {
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(OrderOutboxMessageEntity.class));

        OrderCloseTo payload = new OrderCloseTo();
        payload.setOrderSn("O1");

        service.enqueue("order.close.O1", "ORDER_CLOSE", "O1",
                MqConstants.ORDER_EVENT_EXCHANGE, MqConstants.ORDER_CREATE_ROUTING_KEY, payload);

        ArgumentCaptor<OrderOutboxMessageEntity> captor = ArgumentCaptor.forClass(OrderOutboxMessageEntity.class);
        verify(service).save(captor.capture());
        OrderOutboxMessageEntity saved = captor.getValue();
        assertThat(saved.getMessageKey()).isEqualTo("order.close.O1");
        assertThat(saved.getBusinessType()).isEqualTo("ORDER_CLOSE");
        assertThat(saved.getStatus()).isEqualTo(OrderOutboxStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getPayloadType()).isEqualTo(OrderCloseTo.class.getName());
        assertThat(saved.getPayload()).contains("O1");
    }

    @Test
    void publishReadyMessagesClaimsRowAndSendsDeserializedPayload() throws Exception {
        OrderOutboxMessageEntity message = message(10L, OrderOutboxStatus.PENDING, 0);
        doReturn(List.of(message)).when(service).list(any(Wrapper.class));
        doReturn(message).when(service).getById(10L);
        doReturn(true).when(service).update(any(OrderOutboxMessageEntity.class), any(Wrapper.class));

        int published = service.publishReadyMessages();

        assertThat(published).isEqualTo(1);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MqConstants.ORDER_EVENT_EXCHANGE),
                eq(MqConstants.ORDER_CREATE_ROUTING_KEY),
                payloadCaptor.capture(),
                postProcessorCaptor.capture(),
                correlationCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(OrderCloseTo.class);
        assertThat(((OrderCloseTo) payloadCaptor.getValue()).getOrderSn()).isEqualTo("O1");
        assertThat(correlationCaptor.getValue().getId()).isEqualTo("10");

        Message rabbitMessage = new Message(new byte[0]);
        postProcessorCaptor.getValue().postProcessMessage(rabbitMessage);
        assertThat(rabbitMessage.getMessageProperties().getCorrelationId()).isEqualTo("10");
    }

    @Test
    void markSentOnlyAdvancesSendingRows() {
        doReturn(true).when(service).update(any(OrderOutboxMessageEntity.class), any(Wrapper.class));
        doReturn(true).when(service).update(any(Wrapper.class));

        service.markSent(10L);

        ArgumentCaptor<Wrapper<OrderOutboxMessageEntity>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getCustomSqlSegment()).contains("id", "status");
    }

    @Test
    void markFailedMovesToDeadWhenRetryLimitIsReached() {
        OrderOutboxMessageEntity current = message(10L, OrderOutboxStatus.SENDING, 9);
        doReturn(current).when(service).getById(10L);
        doReturn(true).when(service).update(any(OrderOutboxMessageEntity.class), any(Wrapper.class));

        service.markFailed(10L, "nack");

        ArgumentCaptor<OrderOutboxMessageEntity> captor = ArgumentCaptor.forClass(OrderOutboxMessageEntity.class);
        verify(service).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderOutboxStatus.DEAD);
        assertThat(captor.getValue().getRetryCount()).isEqualTo(10);
        assertThat(captor.getValue().getLastError()).isEqualTo("nack");
    }

    /**
     * 死锁回归。定时任务的两个批量迁移以前是按 status 的范围 UPDATE：先锁二级索引
     * idx_order_outbox_ready 再回表锁主键，而认领/确认是按主键更新再改二级索引 —— 反序死锁。
     * 现在必须是「每条 UPDATE 以主键定位，并在 WHERE 里重新判断原状态」。
     */
    @Test
    void scheduledBatchTransitionsOnlyEverUpdateByPrimaryKey() {
        OrderOutboxMessageEntity stale = message(20L, OrderOutboxStatus.SENDING, 0);
        OrderOutboxMessageEntity exhausted = message(21L, OrderOutboxStatus.FAILED, 10);
        // publishReadyMessages 里 list 的调用顺序：超时回收候选 → 待发消息 → 重试耗尽候选
        doReturn(List.of(stale), List.of(), List.of(exhausted)).when(service).list(any(Wrapper.class));
        doReturn(true).when(service).update(any(OrderOutboxMessageEntity.class), any(Wrapper.class));

        service.publishReadyMessages();

        ArgumentCaptor<OrderOutboxMessageEntity> entities = ArgumentCaptor.forClass(OrderOutboxMessageEntity.class);
        ArgumentCaptor<Wrapper<OrderOutboxMessageEntity>> wrappers = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(entities.capture(), wrappers.capture());
        assertThat(entities.getAllValues()).extracting(OrderOutboxMessageEntity::getStatus)
                .containsExactly(OrderOutboxStatus.FAILED, OrderOutboxStatus.DEAD);
        // 以主键开头（不是 status 范围）+ 仍带原状态条件（CAS，快照读之后被确认的行不会被误改）
        assertThat(wrappers.getAllValues().get(0).getCustomSqlSegment())
                .contains("(id = ", "status = ", "update_time < ");
        assertThat(wrappers.getAllValues().get(1).getCustomSqlSegment())
                .contains("(id = ", "status IN ", "retry_count >= ");
    }

    /**
     * 提交后立即发送失败（十万单里是死锁）时，业务事务已经提交了，
     * 异常若抛到 controller 就是「订单建好了、用户却看到 500」。
     */
    @Test
    void publishFailureAfterCommitNeverReachesTheCaller() {
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doAnswer(inv -> {
            inv.<OrderOutboxMessageEntity>getArgument(0).setId(30L);
            return true;
        }).when(service).save(any(OrderOutboxMessageEntity.class));
        doThrow(new RuntimeException("Deadlock found when trying to get lock")).when(service).getById(30L);

        OrderCloseTo payload = new OrderCloseTo();
        payload.setOrderSn("O1");
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.enqueue("order.close.O1", "ORDER_CLOSE", "O1",
                    MqConstants.ORDER_EVENT_EXCHANGE, MqConstants.ORDER_CREATE_ROUTING_KEY, payload);
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);

            assertThatCode(() -> syncs.forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
            verify(service).getById(30L);   // 确实尝试发送过、确实失败过，不是没走到
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private OrderOutboxMessageEntity message(Long id, Integer status, Integer retryCount) {
        OrderCloseTo payload = new OrderCloseTo();
        payload.setOrderSn("O1");
        OrderOutboxMessageEntity message = new OrderOutboxMessageEntity();
        message.setId(id);
        message.setMessageKey("order.close.O1");
        message.setBusinessType("ORDER_CLOSE");
        message.setBusinessKey("O1");
        message.setExchangeName(MqConstants.ORDER_EVENT_EXCHANGE);
        message.setRoutingKey(MqConstants.ORDER_CREATE_ROUTING_KEY);
        message.setPayloadType(OrderCloseTo.class.getName());
        message.setPayload("{\"orderSn\":\"O1\"}");
        message.setStatus(status);
        message.setRetryCount(retryCount);
        message.setNextRetryTime(new Date(0));
        return message;
    }
}

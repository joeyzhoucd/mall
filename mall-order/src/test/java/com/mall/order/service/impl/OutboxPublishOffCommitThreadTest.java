package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OrderOutboxStatus;
import com.mall.common.to.OrderCloseTo;
import com.mall.order.config.OrderOutboxProperties;
import com.mall.order.entity.OrderOutboxMessageEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 提交后的立即发送不在提交线程上做：afterCommit 时连接还绑在线程上，同步发送会让它多占一段。
 * （2026-09-29，下单连接占用 p50 234ms、SQL 只有 48ms，剩下的就是这段。）
 */
class OutboxPublishOffCommitThreadTest {

    private final List<Runnable> queued = new ArrayList<>();
    private RabbitTemplate rabbit;

    @BeforeEach
    void setUp() {
        rabbit = mock(RabbitTemplate.class);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private OrderOutboxMessageServiceImpl service(Executor executor) {
        OrderOutboxMessageServiceImpl s = spy(new OrderOutboxMessageServiceImpl(rabbit, new ObjectMapper(),
                new OrderOutboxProperties(true, 10_000, 5_000, 100, 10, 5_000, 60_000), executor));
        doReturn(null).when(s).getOne(any(Wrapper.class));
        doAnswer(inv -> {
            ((OrderOutboxMessageEntity) inv.getArgument(0)).setId(10L);
            return true;
        }).when(s).save(any(OrderOutboxMessageEntity.class));
        OrderOutboxMessageEntity pending = new OrderOutboxMessageEntity();
        pending.setId(10L);
        pending.setStatus(OrderOutboxStatus.PENDING);
        pending.setRetryCount(0);
        pending.setExchangeName(MqConstants.ORDER_EVENT_EXCHANGE);
        pending.setRoutingKey(MqConstants.ORDER_CREATE_ROUTING_KEY);
        pending.setPayloadType(OrderCloseTo.class.getName());
        pending.setPayload("{\"orderSn\":\"O1\"}");
        doReturn(pending).when(s).getById(10L);
        doReturn(true).when(s).update(any(OrderOutboxMessageEntity.class), any(Wrapper.class));
        return s;
    }

    private void enqueueAndCommit(OrderOutboxMessageServiceImpl s) {
        OrderCloseTo payload = new OrderCloseTo();
        payload.setOrderSn("O1");
        s.enqueue("order.close.O1", "ORDER_CLOSE", "O1", MqConstants.ORDER_EVENT_EXCHANGE,
                MqConstants.ORDER_CREATE_ROUTING_KEY, payload);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    @Test
    void afterCommitOnlyHandsThePublishToTheExecutor() {
        OrderOutboxMessageServiceImpl s = service(queued::add);

        enqueueAndCommit(s);

        assertThat(queued).as("发送被交给执行器").hasSize(1);
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(s, never()).getById(anyLong());           // 连一次查库都没有在提交线程上做

        queued.get(0).run();                               // 执行器线程上才真正发送
        verify(rabbit).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void aFullQueueFallsBackToTheRelayInsteadOfFailingTheCommit() {
        OrderOutboxMessageServiceImpl s = service(r -> {
            throw new RejectedExecutionException("queue full");
        });

        assertThatCode(() -> enqueueAndCommit(s)).doesNotThrowAnyException();
        verify(rabbit, never()).convertAndSend(anyString(), anyString(), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }
}

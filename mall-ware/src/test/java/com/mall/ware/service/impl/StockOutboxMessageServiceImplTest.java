package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OutboxMessageStatus;
import com.mall.ware.config.StockOutboxProperties;
import com.mall.ware.entity.StockOutboxMessageEntity;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;
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

class StockOutboxMessageServiceImplTest {

    private RabbitTemplate rabbitTemplate;
    private StockOutboxMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        service = spy(new StockOutboxMessageServiceImpl(
                rabbitTemplate,
                new ObjectMapper(),
                new StockOutboxProperties(true, 10_000, 5_000, 100, 10, 5_000, 60_000)
        ));
    }

    @Test
    void enqueuePersistsSerializablePendingMessage() {
        doReturn(null).when(service).getOne(any(Wrapper.class));
        doReturn(true).when(service).save(any(StockOutboxMessageEntity.class));

        WareOrderTaskDetailEntity payload = detail(10L);

        service.enqueue("stock.fail.10", "STOCK_FAIL", "10",
                MqConstants.STOCK_RELEASE_EXCHANGE, MqConstants.STOCK_FAIL_ROUTING_KEY, payload);

        ArgumentCaptor<StockOutboxMessageEntity> captor = ArgumentCaptor.forClass(StockOutboxMessageEntity.class);
        verify(service).save(captor.capture());
        StockOutboxMessageEntity saved = captor.getValue();
        assertThat(saved.getMessageKey()).isEqualTo("stock.fail.10");
        assertThat(saved.getBusinessType()).isEqualTo("STOCK_FAIL");
        assertThat(saved.getStatus()).isEqualTo(OutboxMessageStatus.PENDING);
        assertThat(saved.getRetryCount()).isZero();
        assertThat(saved.getPayloadType()).isEqualTo(WareOrderTaskDetailEntity.class.getName());
        assertThat(saved.getPayload()).contains("10");
    }

    @Test
    void publishReadyMessagesClaimsRowAndSendsDeserializedPayload() throws Exception {
        StockOutboxMessageEntity message = message(99L, OutboxMessageStatus.PENDING, 0);
        doReturn(List.of(message)).when(service).list(any(Wrapper.class));
        doReturn(message).when(service).getById(99L);
        doReturn(true).when(service).update(any(StockOutboxMessageEntity.class), any(Wrapper.class));

        int published = service.publishReadyMessages();

        assertThat(published).isEqualTo(1);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(MqConstants.STOCK_RELEASE_EXCHANGE),
                eq(MqConstants.STOCK_FAIL_ROUTING_KEY),
                payloadCaptor.capture(),
                postProcessorCaptor.capture(),
                correlationCaptor.capture());
        assertThat(payloadCaptor.getValue()).isInstanceOf(WareOrderTaskDetailEntity.class);
        assertThat(((WareOrderTaskDetailEntity) payloadCaptor.getValue()).getId()).isEqualTo(10L);
        assertThat(correlationCaptor.getValue().getId()).isEqualTo("99");

        Message rabbitMessage = new Message(new byte[0]);
        postProcessorCaptor.getValue().postProcessMessage(rabbitMessage);
        assertThat(rabbitMessage.getMessageProperties().getCorrelationId()).isEqualTo("99");
    }

    @Test
    void markFailedMovesToDeadWhenRetryLimitIsReached() {
        StockOutboxMessageEntity current = message(99L, OutboxMessageStatus.SENDING, 9);
        doReturn(current).when(service).getById(99L);
        doReturn(true).when(service).update(any(StockOutboxMessageEntity.class), any(Wrapper.class));

        service.markFailed(99L, "nack");

        ArgumentCaptor<StockOutboxMessageEntity> captor = ArgumentCaptor.forClass(StockOutboxMessageEntity.class);
        verify(service).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo(OutboxMessageStatus.DEAD);
        assertThat(captor.getValue().getRetryCount()).isEqualTo(10);
        assertThat(captor.getValue().getLastError()).isEqualTo("nack");
    }

    /**
     * 死锁回归。定时任务的两个批量迁移以前是按 status 的范围 UPDATE：先锁二级索引
     * idx_stock_outbox_ready 再回表锁主键，而认领/确认是按主键更新再改二级索引 —— 反序死锁。
     * 现在必须是「每条 UPDATE 以主键定位，并在 WHERE 里重新判断原状态」。
     */
    @Test
    void scheduledBatchTransitionsOnlyEverUpdateByPrimaryKey() {
        StockOutboxMessageEntity stale = message(20L, OutboxMessageStatus.SENDING, 0);
        StockOutboxMessageEntity exhausted = message(21L, OutboxMessageStatus.FAILED, 10);
        // publishReadyMessages 里 list 的调用顺序：超时回收候选 → 待发消息 → 重试耗尽候选
        doReturn(List.of(stale), List.of(), List.of(exhausted)).when(service).list(any(Wrapper.class));
        doReturn(true).when(service).update(any(StockOutboxMessageEntity.class), any(Wrapper.class));

        service.publishReadyMessages();

        ArgumentCaptor<StockOutboxMessageEntity> entities = ArgumentCaptor.forClass(StockOutboxMessageEntity.class);
        ArgumentCaptor<Wrapper<StockOutboxMessageEntity>> wrappers = ArgumentCaptor.forClass(Wrapper.class);
        verify(service, times(2)).update(entities.capture(), wrappers.capture());
        assertThat(entities.getAllValues()).extracting(StockOutboxMessageEntity::getStatus)
                .containsExactly(OutboxMessageStatus.FAILED, OutboxMessageStatus.DEAD);
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
            inv.<StockOutboxMessageEntity>getArgument(0).setId(30L);
            return true;
        }).when(service).save(any(StockOutboxMessageEntity.class));
        doThrow(new RuntimeException("Deadlock found when trying to get lock")).when(service).getById(30L);

        WareOrderTaskDetailEntity payload = detail(10L);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.enqueue("stock.fail.10", "STOCK_FAIL", "10",
                    MqConstants.STOCK_RELEASE_EXCHANGE, MqConstants.STOCK_FAIL_ROUTING_KEY, payload);
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);

            assertThatCode(() -> syncs.forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
            verify(service).getById(30L);   // 确实尝试发送过、确实失败过，不是没走到
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private StockOutboxMessageEntity message(Long id, Integer status, Integer retryCount) {
        StockOutboxMessageEntity message = new StockOutboxMessageEntity();
        message.setId(id);
        message.setMessageKey("stock.fail.10");
        message.setBusinessType("STOCK_FAIL");
        message.setBusinessKey("10");
        message.setExchangeName(MqConstants.STOCK_RELEASE_EXCHANGE);
        message.setRoutingKey(MqConstants.STOCK_FAIL_ROUTING_KEY);
        message.setPayloadType(WareOrderTaskDetailEntity.class.getName());
        message.setPayload("{\"id\":10,\"skuId\":1001,\"skuNum\":2,\"taskId\":20,\"retryCount\":5}");
        message.setStatus(status);
        message.setRetryCount(retryCount);
        message.setNextRetryTime(new Date(0));
        return message;
    }

    private WareOrderTaskDetailEntity detail(Long id) {
        WareOrderTaskDetailEntity detail = new WareOrderTaskDetailEntity();
        detail.setId(id);
        detail.setSkuId(1001L);
        detail.setSkuNum(2);
        detail.setTaskId(20L);
        detail.setRetryCount(5);
        return detail;
    }
}

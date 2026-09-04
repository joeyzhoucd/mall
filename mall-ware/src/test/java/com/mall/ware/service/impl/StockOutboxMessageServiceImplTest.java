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
import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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

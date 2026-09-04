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

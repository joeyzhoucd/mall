package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConsumeStatus;
import com.mall.order.entity.OrderMqConsumeMessageEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class OrderMqConsumeMessageServiceImplTest {

    private OrderMqConsumeMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new OrderMqConsumeMessageServiceImpl());
    }

    @Test
    void consumeOnceClaimsAndMarksSuccess() {
        doReturn(true).when(service).save(any(OrderMqConsumeMessageEntity.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        AtomicInteger runs = new AtomicInteger();

        boolean consumed = service.consumeOnce("order-close-listener", "order.close:O1",
                "ORDER_CLOSE", runs::incrementAndGet);

        assertThat(consumed).isTrue();
        assertThat(runs).hasValue(1);
        ArgumentCaptor<OrderMqConsumeMessageEntity> captor = ArgumentCaptor.forClass(OrderMqConsumeMessageEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MqConsumeStatus.PROCESSING);
        assertThat(captor.getValue().getConsumeCount()).isEqualTo(1);
        verify(service).update(any(Wrapper.class));
    }

    @Test
    void consumeOnceSkipsAlreadySuccessfulMessage() {
        OrderMqConsumeMessageEntity existing = new OrderMqConsumeMessageEntity();
        existing.setId(1L);
        existing.setStatus(MqConsumeStatus.SUCCESS);
        doThrow(new DuplicateKeyException("duplicate")).when(service).save(any(OrderMqConsumeMessageEntity.class));
        doReturn(existing).when(service).getOne(any(Wrapper.class));
        AtomicInteger runs = new AtomicInteger();

        boolean consumed = service.consumeOnce("order-close-listener", "order.close:O1",
                "ORDER_CLOSE", runs::incrementAndGet);

        assertThat(consumed).isFalse();
        assertThat(runs).hasValue(0);
        verify(service, never()).update(any(Wrapper.class));
    }

    @Test
    void consumeOnceMarksFailedAndRethrows() {
        doReturn(true).when(service).save(any(OrderMqConsumeMessageEntity.class));
        doReturn(true).when(service).update(any(Wrapper.class));

        assertThatThrownBy(() -> service.consumeOnce("order-close-listener", "order.close:O1",
                "ORDER_CLOSE", () -> {
                    throw new IllegalStateException("boom");
                })).isInstanceOf(IllegalStateException.class);

        verify(service).save(any(OrderMqConsumeMessageEntity.class));
        verify(service).update(any(Wrapper.class));
    }
}

package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.constant.MqConsumeStatus;
import com.mall.ware.entity.WareMqConsumeMessageEntity;
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

class WareMqConsumeMessageServiceImplTest {

    private WareMqConsumeMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = spy(new WareMqConsumeMessageServiceImpl());
    }

    @Test
    void consumeOnceClaimsAndMarksSuccess() {
        doReturn(true).when(service).save(any(WareMqConsumeMessageEntity.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        AtomicInteger runs = new AtomicInteger();

        boolean consumed = service.consumeOnce("stock-deduct-listener", "stock.deduct:O1",
                "STOCK_DEDUCT", runs::incrementAndGet);

        assertThat(consumed).isTrue();
        assertThat(runs).hasValue(1);
        ArgumentCaptor<WareMqConsumeMessageEntity> captor = ArgumentCaptor.forClass(WareMqConsumeMessageEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MqConsumeStatus.PROCESSING);
        assertThat(captor.getValue().getConsumeCount()).isEqualTo(1);
        verify(service).update(any(Wrapper.class));
    }

    @Test
    void consumeOnceSkipsAlreadySuccessfulMessage() {
        WareMqConsumeMessageEntity existing = new WareMqConsumeMessageEntity();
        existing.setId(1L);
        existing.setStatus(MqConsumeStatus.SUCCESS);
        doThrow(new DuplicateKeyException("duplicate")).when(service).save(any(WareMqConsumeMessageEntity.class));
        doReturn(existing).when(service).getOne(any(Wrapper.class));
        AtomicInteger runs = new AtomicInteger();

        boolean consumed = service.consumeOnce("stock-deduct-listener", "stock.deduct:O1",
                "STOCK_DEDUCT", runs::incrementAndGet);

        assertThat(consumed).isFalse();
        assertThat(runs).hasValue(0);
        verify(service, never()).update(any(Wrapper.class));
    }

    @Test
    void consumeOnceMarksFailedAndRethrows() {
        doReturn(true).when(service).save(any(WareMqConsumeMessageEntity.class));
        doReturn(true).when(service).update(any(Wrapper.class));

        assertThatThrownBy(() -> service.consumeOnce("stock-deduct-listener", "stock.deduct:O1",
                "STOCK_DEDUCT", () -> {
                    throw new IllegalStateException("boom");
                })).isInstanceOf(IllegalStateException.class);

        verify(service).save(any(WareMqConsumeMessageEntity.class));
        verify(service).update(any(Wrapper.class));
    }
}

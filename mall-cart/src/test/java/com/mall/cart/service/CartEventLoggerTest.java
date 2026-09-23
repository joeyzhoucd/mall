package com.mall.cart.service;

import com.mall.cart.feign.MemberCartLogFeignService;
import com.mall.cart.to.CartLogTo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 埋点投递器的行为验证。
 *
 * <h3>为什么这几条必须有测试</h3>
 * 这个组件的失败模式全都是<b>静默</b>的：队列满了照样返回、下游挂了照样返回、
 * 没开 {@code @EnableScheduling} 的话事件只进不出 —— 业务侧一切正常，
 * 只有几十万条埋点悄悄不见了。这类东西不测就等于没做。
 */
class CartEventLoggerTest {

    private MemberCartLogFeignService feign;
    private CartEventLogger logger;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        feign = mock(MemberCartLogFeignService.class);
        registry = new SimpleMeterRegistry();
        logger = new CartEventLogger(registry);
        ReflectionTestUtils.setField(logger, "feign", feign);
        ReflectionTestUtils.setField(logger, "enabled", true);
    }

    private double counter(String result) {
        return registry.find("mall_cart_event_log").tag("result", result).counter().count();
    }

    @Test
    @DisplayName("正常路径：入队后 flush 会把事件投出去")
    void recordsAndFlushes() {
        logger.record(1L, 100L, 200L, CartLogTo.ACTION_ADD, 2);
        assertThat(counter("accepted")).isEqualTo(1);

        logger.flush();

        @SuppressWarnings("unchecked")
        List<CartLogTo>[] captured = new List[1];
        org.mockito.ArgumentCaptor<List<CartLogTo>> cap = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feign).batch(cap.capture());
        captured[0] = cap.getValue();
        assertThat(captured[0]).hasSize(1);
        assertThat(captured[0].get(0).getSpuId()).isEqualTo(200L);
        assertThat(captured[0].get(0).getAction()).isEqualTo(CartLogTo.ACTION_ADD);
    }

    @Test
    @DisplayName("队列满了只丢事件，record 绝不抛异常、绝不阻塞")
    void dropsWhenFull() {
        // 容量 10000，灌 10100 条
        for (int i = 0; i < 10100; i++) {
            logger.record(1L, (long) i, 200L, CartLogTo.ACTION_ADD, 1);
        }
        assertThat(counter("accepted"))
                .as("接受的条数应该正好等于队列容量")
                .isEqualTo(10000);
        assertThat(counter("dropped"))
                .as("超出的应该被丢掉而不是阻塞或抛异常")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("下游抛异常时不能冒泡，只记失败计数")
    void swallowsDownstreamFailure() {
        doThrow(new RuntimeException("mall-member 挂了")).when(feign).batch(anyList());
        logger.record(1L, 100L, 200L, CartLogTo.ACTION_ADD, 1);

        logger.flush();   // 不抛就是通过

        assertThat(counter("failed")).isEqualTo(1);
    }

    @Test
    @DisplayName("spuId 为空的条目直接跳过——它没法参与 SPU 粒度的共现统计")
    void skipsItemsWithoutSpuId() {
        logger.record(1L, 100L, null, CartLogTo.ACTION_ADD, 1);
        logger.record(1L, null, 200L, CartLogTo.ACTION_ADD, 1);
        assertThat(counter("accepted")).isZero();

        logger.flush();
        org.mockito.Mockito.verify(feign, org.mockito.Mockito.never()).batch(anyList());
    }

    @Test
    @DisplayName("单批不超过上限——一条 SQL 撑爆 max_allowed_packet 会让整批失败")
    void capsBatchSize() {
        for (int i = 0; i < 1200; i++) {
            logger.record(1L, (long) i, 200L, CartLogTo.ACTION_ADD, 1);
        }
        when(feign.batch(anyList())).thenReturn(null);

        logger.flush();

        org.mockito.ArgumentCaptor<List<CartLogTo>> cap = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(feign).batch(cap.capture());
        assertThat(cap.getValue()).hasSizeLessThanOrEqualTo(500);
    }

    @Test
    @DisplayName("开关关掉之后完全不工作")
    void respectsDisableSwitch() {
        ReflectionTestUtils.setField(logger, "enabled", false);
        logger.record(1L, 100L, 200L, CartLogTo.ACTION_ADD, 1);
        assertThat(counter("accepted")).isZero();
        assertThat(counter("dropped")).isZero();
    }

    @Test
    @DisplayName("停机时会把剩下的排空")
    void drainsOnShutdown() {
        List<CartLogTo> ignored = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            logger.record(1L, (long) i, 200L, CartLogTo.ACTION_ADD, 1);
        }
        logger.drainOnShutdown();
        // 900 条、单批 500，应该发两批
        org.mockito.Mockito.verify(feign, org.mockito.Mockito.times(2)).batch(anyList());
        assertThat(ignored).isEmpty();
    }
}

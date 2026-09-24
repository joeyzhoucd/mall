package com.mall.mq.config;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 消费失败要先原地重试，用尽了才进死信队列。
 *
 * <h3>为什么需要守</h3>
 * 十万单实测：关单消费者因为一次死锁、一次在线 DDL（1412 Table definition has changed）
 * 就直接进了 DLQ。这些错误重试一次就会好，却要等人工重放 ——
 * 没人重放的两单永远停在待付款，库存也一直锁着，而且<b>不报任何错</b>。
 * <p>
 * 这里调用的是工厂上真实挂着的 advice，断言的是行为（调了几次、最后抛什么），
 * 不是「工厂上有没有一个 advice」—— 后者在 maxRetries 被改成 0 时照样是绿的。
 */
class MqListenerRetryTest {

    private static MethodInterceptor retryAdvice() {
        MallMqProperties properties = new MallMqProperties();
        properties.getListener().setRetryInitialIntervalMs(1);   // 测试里不真等 1s/2s
        properties.getListener().setRetryMaxIntervalMs(2);
        SimpleRabbitListenerContainerFactory factory = new MallMqAutoConfiguration().rabbitListenerContainerFactory(
                mock(ConnectionFactory.class), mock(MessageConverter.class), properties);
        Advice[] chain = factory.getAdviceChain();
        assertNotNull(chain, "监听工厂没有挂重试 advice —— 一次瞬时失败就会直接进 DLQ");
        assertEquals(1, chain.length);
        return (MethodInterceptor) chain[0];
    }

    /** 容器调用 advice 时的参数形状是 (Channel, Message)，恢复器从第二个参数取消息。 */
    private static MethodInvocation listenerCallFailing(AtomicInteger calls, int failuresBeforeSuccess) throws Throwable {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[] {null, new Message(new byte[0])});
        when(invocation.proceed()).thenAnswer(inv -> {
            if (calls.incrementAndGet() <= failuresBeforeSuccess) {
                throw new RuntimeException("Deadlock found when trying to get lock");
            }
            return null;
        });
        return invocation;
    }

    @Test
    @DisplayName("瞬时失败（前两次失败、第三次成功）不进 DLQ")
    void transientFailureIsRetriedInPlace() throws Throwable {
        AtomicInteger calls = new AtomicInteger();

        retryAdvice().invoke(listenerCallFailing(calls, 2));

        assertEquals(3, calls.get(), "应该是 1 次 + 重试 2 次");
    }

    @Test
    @DisplayName("一直失败：试满 3 次后拒绝且不重新入队 —— 进 DLQ，而不是无限重投")
    void persistentFailureEndsInDlqAfterRetries() throws Throwable {
        AtomicInteger calls = new AtomicInteger();

        Throwable thrown = assertThrows(Throwable.class,
                () -> retryAdvice().invoke(listenerCallFailing(calls, Integer.MAX_VALUE)));

        assertEquals(3, calls.get(), "重试次数不对");
        boolean rejected = false;
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            rejected |= t instanceof AmqpRejectAndDontRequeueException;
        }
        assertTrue(rejected, "用尽后必须是 reject-and-don't-requeue（才会进 DLQ），实际是 " + thrown);
    }
}

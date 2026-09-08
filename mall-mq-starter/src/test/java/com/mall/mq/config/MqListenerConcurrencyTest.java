package com.mall.mq.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 守住消息监听容器的并发和预取被<b>显式配置过</b>。
 *
 * <h3>为什么需要守</h3>
 * Spring AMQP 的默认组合是 {@code concurrentConsumers=1} + {@code prefetchCount=250}。
 * 一个消费者抓走 250 条未确认消息、却一条一条处理 —— 在 2 副本下表现为
 * 「一个 pod 压着 250 条积压，另一个几乎空闲」。
 * <p>
 * 这个缺陷<b>不会以错误的形式出现</b>：消息最终都会被处理，幂等表也挡住了重投的重复。
 * 唯一的症状是突发流量下延迟分布很难看，而那需要有人去看队列深度才会发现。
 * 删掉那两行 setter 就会静默回到这个状态。
 */
class MqListenerConcurrencyTest {

    private static SimpleRabbitListenerContainerFactory factory(MallMqProperties properties) {
        return new MallMqAutoConfiguration().rabbitListenerContainerFactory(
                mock(ConnectionFactory.class), mock(MessageConverter.class), properties);
    }

    /**
     * 把 Spring 的默认值钉在测试里。
     *
     * <p>本模块的配置是<b>针对这两个默认值</b>做的。如果哪天 Spring 改了它们
     * （比如把 prefetch 默认降下来），这条断言会失败 —— 那是在提醒我们
     * 重新评估自己的取值，而不是让配置和一个已经变了的前提继续并存。
     * <p>250 这个数是从 spring-rabbit 4.1.1 的字节码读出来的，不是凭记忆。
     */
    @Test
    @DisplayName("Spring AMQP 的默认 prefetch 仍然是 250 —— 本模块的配置是针对它做的")
    void springDefaultPrefetchIsStillTheOneWeCompensateFor() {
        assertEquals(250, AbstractMessageListenerContainer.DEFAULT_PREFETCH_COUNT,
                "Spring 改了默认 prefetch，请重新评估 mall.mq.listener.prefetch 的取值");
    }

    @Test
    @DisplayName("工厂必须显式设过并发和预取，不能留给 Spring 的默认值")
    void factorySetsBothExplicitly() {
        SimpleRabbitListenerContainerFactory f = factory(new MallMqProperties());

        Object concurrency = ReflectionTestUtils.getField(f, "concurrentConsumers");
        Object prefetch = ReflectionTestUtils.getField(f, "prefetchCount");

        assertNotNull(concurrency, "没有显式设 concurrentConsumers —— 会退回 Spring 默认");
        assertNotNull(prefetch, "没有显式设 prefetchCount —— 会退回 Spring 默认 250");
    }

    /**
     * 预取必须<b>远小于</b> Spring 的默认值。
     *
     * <p>这里断言的是"小"而不是某个具体数字：具体取多少（2 还是 4 还是 8）
     * 属于可以调的参数，而"必须小"是这个场景的结论 ——
     * 消费者做的是毫秒级的 DB 事务，broker 往返是亚毫秒级的集群内网，
     * 1~2 条就足以让消费者永不空转，再多只会破坏副本间的公平分配。
     */
    @Test
    @DisplayName("预取要小（慢消费者），至少比 Spring 默认小一个数量级")
    void prefetchIsSmallForSlowConsumers() {
        int prefetch = (Integer) ReflectionTestUtils.getField(factory(new MallMqProperties()), "prefetchCount");

        assertTrue(prefetch > 0, "预取必须 >= 1，否则消费者拿不到消息：" + prefetch);
        assertTrue(prefetch <= AbstractMessageListenerContainer.DEFAULT_PREFETCH_COUNT / 10,
                "预取 " + prefetch + " 对 DB 型消费者来说太大 —— "
                        + "一个消费者会抓走一大批未确认消息，另一个副本拿不到活干");
    }

    @Test
    @DisplayName("并发和预取都能从配置改，不需要改代码")
    void bothAreConfigurable() {
        MallMqProperties properties = new MallMqProperties();
        properties.getListener().setConcurrency(3);
        properties.getListener().setPrefetch(9);

        SimpleRabbitListenerContainerFactory f = factory(properties);

        assertEquals(3, ReflectionTestUtils.getField(f, "concurrentConsumers"),
                "concurrency 没有从配置读进去");
        assertEquals(9, ReflectionTestUtils.getField(f, "prefetchCount"),
                "prefetch 没有从配置读进去");
    }

    /**
     * 并发默认保持 1。
     *
     * <p>这条断言的意义不是"1 是最优值"，而是<b>不要在没有压测数据的情况下把它调高</b>：
     * 天花板不在消费者数量上 —— Hikari 的 maximum-pool-size 是 5，
     * 而那 5 个连接和 HTTP 请求共用。调高 concurrency 可能只是把排队从 MQ
     * 挪到连接池，还顺带饿着 HTTP。
     * <p>
     * 真要改，连同这条断言一起改，并在提交信息里写上是哪次压测得出的数。
     */
    @Test
    @DisplayName("并发默认是 1 —— 调高它需要压测数据，改这里时请一并给出依据")
    void concurrencyDefaultsToOneUntilMeasured() {
        assertEquals(1, ReflectionTestUtils.getField(factory(new MallMqProperties()), "concurrentConsumers"),
                "并发默认值被改了。这不是错，但需要依据："
                        + "Hikari 池只有 5 且与 HTTP 共用，先量单条消息处理耗时和连接池占用");
    }

    @Test
    @DisplayName("消费失败不重新入队 —— 靠死信队列，而不是无限重投")
    void failedMessagesGoToDlqNotBackToTheQueue() {
        SimpleRabbitListenerContainerFactory f = factory(new MallMqProperties());

        assertEquals(Boolean.FALSE, ReflectionTestUtils.getField(f, "defaultRequeueRejected"),
                "requeue 打开的话，一条处理不了的消息会被无限重投，"
                        + "把队列堵死并且永远进不了死信队列");
    }
}

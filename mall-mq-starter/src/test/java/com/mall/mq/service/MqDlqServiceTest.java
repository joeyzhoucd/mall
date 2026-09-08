package com.mall.mq.service;

import com.mall.common.constant.MqConstants;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.impl.LongStringHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住死信队列的两个不变式：<b>peek 不改变队列</b>、<b>replay 不丢消息</b>。
 *
 * <h3>这个测试对应的是一个真实存在过的缺陷（2026-09-06 修）</h3>
 * 原实现用 {@code rabbitTemplate.receive(dlq)}，那是<b>自动确认</b>的：
 * 方法一返回，消息在 broker 上就已经没了。之后再 send 出去。
 * 于是 send 一旦失败（channel 被关、交换机被删、网络抖动），
 * 这条消息既没回到源队列、也不在死信队列里 —— 永久丢失。
 * 而死信队列存在的全部意义就是「不丢」。
 *
 * <h3>为什么这个缺陷不会被普通测试或人工验证发现</h3>
 * 正常路径完全正常：点重投、消息确实回到了源队列、计数也对。
 * 只有在 publish 失败的那一次才会丢，而那一次不会有任何报错指向"消息没了" ——
 * 只会看到一个异常，然后队列深度少了 1。
 * 所以必须<b>显式构造 publish 失败</b>来测，这也正是下面那条测试在做的事。
 */
class MqDlqServiceTest {

    private RabbitTemplate rabbitTemplate;
    private Channel channel;
    private MqDlqService service;

    private static final String DLQ = MqConstants.ORDER_RELEASE_DLQ;
    private static final long TAG = 42L;

    @BeforeEach
    void setUp() throws Exception {
        rabbitTemplate = mock(RabbitTemplate.class);
        channel = mock(Channel.class);

        // execute(callback) 直接把 mock 出来的 channel 交给回调，
        // 这样被测的就是回调里那段真实逻辑，而不是别的什么东西。
        when(rabbitTemplate.execute(any())).thenAnswer(invocation -> {
            ChannelCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(channel);
        });

        service = new MqDlqService(rabbitTemplate, mock(RabbitAdmin.class));
    }

    // -----------------------------------------------------------------------
    // replay：不能丢
    // -----------------------------------------------------------------------

    /**
     * <b>本文件的核心断言。</b>
     *
     * <p>publish 抛异常时，必须 nack + requeue，<b>绝不能 ack</b>。
     * ack 掉就等于把一条本来还能救的消息永久删除了。
     */
    @Test
    @DisplayName("重投失败时把消息放回死信队列，绝不能 ack 掉")
    void replayRequeuesWhenPublishFails() throws Exception {
        when(channel.basicGet(eq(DLQ), eq(false))).thenReturn(response("{\"orderSn\":\"X1\"}"));
        // 交换机被删、channel 被关、网络抖动 —— 都长这个样子。
        // basicPublish 返回 void，所以必须用 doThrow(...).when(...) 这个方向。
        doThrow(new IOException("channel closed"))
                .when(channel).basicPublish(anyString(), anyString(), any(), any());

        assertThrows(Exception.class, () -> service.replay(DLQ, 1),
                "publish 失败应当把异常抛出去，而不是静静地吞掉");

        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("重投成功后才 ack")
    void replayAcksAfterSuccessfulPublish() throws Exception {
        when(channel.basicGet(eq(DLQ), eq(false)))
                .thenReturn(response("{\"orderSn\":\"X1\"}"))
                .thenReturn(null);

        assertEquals(1, service.replay(DLQ, 5), "应当报告重投了 1 条");

        verify(channel).basicPublish(eq(MqConstants.ORDER_EVENT_EXCHANGE),
                eq(MqConstants.ORDER_RELEASE_ROUTING_KEY), any(), any());
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    /**
     * basicGet 的第二个参数必须是 false（不自动确认）。
     *
     * <p>这一条单独守，是因为它就是原缺陷的根源：
     * 传 true 的话上面那条"失败要 requeue"的逻辑<b>根本没有机会执行</b> ——
     * 消息在 basicGet 返回的那一刻就已经没了。
     * 而代码看起来仍然有完整的 try/catch/nack，读起来完全正确。
     */
    @Test
    @DisplayName("basicGet 必须关掉自动确认，否则 requeue 逻辑形同虚设")
    void replayNeverUsesAutoAck() throws Exception {
        when(channel.basicGet(anyString(), anyBoolean())).thenReturn(null);
        service.replay(DLQ, 1);
        verify(channel).basicGet(DLQ, false);
        verify(channel, never()).basicGet(anyString(), eq(true));
    }

    @Test
    @DisplayName("队列空了就停，不会把 limit 次都跑满")
    void replayStopsWhenQueueIsEmpty() throws Exception {
        when(channel.basicGet(eq(DLQ), eq(false)))
                .thenReturn(response("a"))
                .thenReturn(null);

        assertEquals(1, service.replay(DLQ, 10));
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    // -----------------------------------------------------------------------
    // peek：不能改变队列
    // -----------------------------------------------------------------------

    /**
     * peek 必须 nack + requeue，让消息回到<b>队首</b>。
     *
     * <p>原实现是 receive 之后 {@code send("", dlq, message)}，
     * 那是把消息放回<b>队尾</b> —— 「看一眼」会改变队列顺序，
     * 而且中间同样有丢失窗口。
     */
    @Test
    @DisplayName("peek 把消息放回去，不消费也不改变顺序")
    void peekRequeuesInsteadOfConsuming() throws Exception {
        when(channel.basicGet(eq(DLQ), eq(false))).thenReturn(response("{\"orderSn\":\"X1\"}"));

        MqDlqService.DlqMessageView view = service.peek(DLQ);

        assertNotNull(view);
        assertEquals("{\"orderSn\":\"X1\"}", view.body());
        verify(channel).basicNack(TAG, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        // 绝不能再往队列里 send 一份 —— 那会变成"放回队尾"，而且可能重复。
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("队列为空时 peek 返回 null，不抛异常")
    void peekReturnsNullOnEmptyQueue() throws Exception {
        when(channel.basicGet(anyString(), anyBoolean())).thenReturn(null);
        assertNull(service.peek(DLQ));
    }

    // -----------------------------------------------------------------------
    // 队列白名单
    // -----------------------------------------------------------------------

    /**
     * 队列名必须来自白名单。
     *
     * <p>这三个接口能收发任意队列的消息。如果调用方可以自由指定队列名，
     * 等于把 broker 的读写权限整个暴露给了后台 ——
     * 一个 discard 请求就能清空任意业务队列，而那不是死信。
     */
    @Test
    @DisplayName("白名单之外的队列名一律拒绝")
    void rejectsQueuesOutsideTheBindingTable() {
        for (String name : new String[] { "some.other.queue", "", "mall.order.release.queue" }) {
            assertThrows(IllegalArgumentException.class, () -> service.peek(name),
                    "队列名 «" + name + "» 不在白名单里，应当被拒绝");
        }
    }

    @Test
    @DisplayName("源队列名本身也不在白名单里 —— 白名单只放死信队列")
    void bindingTableContainsOnlyDeadLetterQueues() {
        List<String> dlqs = new ArrayList<>();
        for (MqDlqService.DlqBinding binding : MqDlqService.bindings()) {
            dlqs.add(binding.dlq());
            assertTrue(!binding.dlq().equals(binding.sourceQueue()),
                    "死信队列和源队列不该同名：" + binding.dlq());
        }
        assertEquals(5, dlqs.size(), "绑定表条数变了，确认是有意的：" + dlqs);
    }

    // -----------------------------------------------------------------------
    // 一览：把"不存在"和"空"区分开
    // -----------------------------------------------------------------------

    /**
     * 死信队列不存在时必须报 {@code dlqExists=false}，而不是"深度 0"。
     *
     * <h3>这条对应 2026-09-08 实际发生的故障</h3>
     * 那五个消费队列建于死信配置之前，参数不匹配导致声明失败（406），
     * 而 RabbitAdmin 的声明是成批的、第一个失败整批中止 ——
     * 所以五个死信队列<b>一个都没被创建</b>。
     * <p>
     * 而 {@code getQueueProperties} 对不存在的队列返回 null，原实现把它当成了 0，
     * 于是控制台显示"5 条绑定、深度全 0"，看着非常健康。
     * <b>一个显示"一切正常"的运维页面比没有页面更糟，因为它让人停止怀疑。</b>
     */
    @Test
    @DisplayName("死信队列不存在时要报 dlqExists=false，不能显示成「深度 0」")
    void overviewDistinguishesMissingQueueFromEmptyQueue() {
        RabbitAdmin admin = mock(RabbitAdmin.class);
        // 全部返回 null = 队列都不存在，也就是那次故障的状态
        when(admin.getQueueProperties(anyString())).thenReturn(null);

        MqDlqService svc = new MqDlqService(mock(RabbitTemplate.class), admin);

        for (MqDlqService.DlqQueueView v : svc.overview()) {
            assertFalse(v.dlqExists(),
                    v.dlq() + " 不存在，但 dlqExists 报了 true —— "
                            + "控制台会把「死信机制没接通」显示成「一切正常」");
            assertEquals(0, v.messageCount(), "不存在的队列深度应当是 0（但要靠 dlqExists 表达「不存在」）");
        }
    }

    /**
     * 源队列没有消费者时要报出来。
     *
     * <p>那次 {@code order.release.order.queue} 的消费者数是 <b>0</b> ——
     * 意味着超时订单永远不关、锁定库存永远不释放。
     * 而这件事在任何常规监控里都是绿的：队列存在、深度 0、pod Ready。
     */
    @Test
    @DisplayName("源队列存在但没有消费者时要如实报 0，那意味着消息只会堆积")
    void overviewReportsSourceConsumerCount() {
        Properties existsNoConsumer = new Properties();
        existsNoConsumer.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 0);
        existsNoConsumer.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 0);

        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueProperties(anyString())).thenReturn(existsNoConsumer);

        MqDlqService svc = new MqDlqService(mock(RabbitTemplate.class), admin);

        for (MqDlqService.DlqQueueView v : svc.overview()) {
            assertTrue(v.dlqExists(), "队列存在却报 dlqExists=false");
            assertTrue(v.sourceExists(), "源队列存在却报 sourceExists=false");
            assertEquals(0, v.sourceConsumers(),
                    v.sourceQueue() + " 的消费者数没有如实报出来");
        }
    }

    @Test
    @DisplayName("一切正常时两个存在标志都为 true、消费者数如实反映")
    void overviewReportsHealthyState() {
        Properties healthy = new Properties();
        healthy.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 3);
        healthy.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);

        RabbitAdmin admin = mock(RabbitAdmin.class);
        when(admin.getQueueProperties(anyString())).thenReturn(healthy);

        List<MqDlqService.DlqQueueView> views =
                new MqDlqService(mock(RabbitTemplate.class), admin).overview();

        assertEquals(5, views.size(), "绑定表应当有 5 条");
        for (MqDlqService.DlqQueueView v : views) {
            assertTrue(v.dlqExists());
            assertTrue(v.sourceExists());
            assertEquals(3, v.messageCount());
            assertEquals(2, v.sourceConsumers());
        }
    }

    // -----------------------------------------------------------------------
    // 头字段
    // -----------------------------------------------------------------------

    /**
     * x-death 是死信页面上唯一能回答「为什么进来的」的东西。
     *
     * <p>驱动把字符串包成 LongString，Jackson 序列化它时输出的是对象内部字段
     * 而不是字符串本身，界面上会看到一堆无意义的结构。
     * 而且 x-death 是 {@code List<Map<...>>}，嵌套的也要处理。
     */
    @Test
    @DisplayName("x-death 里嵌套的 LongString 也要转成字符串")
    void normalizesNestedLongStringsInXDeath() {
        Map<String, Object> death = new LinkedHashMap<>();
        death.put("reason", LongStringHelper.asLongString("rejected"));
        death.put("queue", LongStringHelper.asLongString("mall.order.release.queue"));
        death.put("count", 3L);

        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("x-death", List.of(death));
        headers.put("plain", LongStringHelper.asLongString("value"));

        Object normalized = MqDlqService.normalizeHeaders((Object) headers);

        assertInstanceOf(Map.class, normalized);
        Map<?, ?> result = (Map<?, ?>) normalized;
        assertEquals("value", result.get("plain"), "顶层 LongString 没转成字符串");

        List<?> deaths = (List<?>) result.get("x-death");
        Map<?, ?> first = (Map<?, ?>) deaths.get(0);
        assertEquals("rejected", first.get("reason"), "嵌套的 LongString 没转成字符串");
        assertEquals("mall.order.release.queue", first.get("queue"));
        assertEquals(3L, first.get("count"), "数字不该被动过");
    }

    @Test
    @DisplayName("byte[] 头字段按 UTF-8 解出来，不能变成数组的字符串形式")
    void normalizesByteArrayHeaders() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("trace", "订单关闭".getBytes(StandardCharsets.UTF_8));

        Map<?, ?> result = (Map<?, ?>) MqDlqService.normalizeHeaders((Object) headers);
        assertEquals("订单关闭", result.get("trace"));
    }

    // -----------------------------------------------------------------------

    private static GetResponse response(String body) {
        Envelope envelope = new Envelope(TAG, false, "mall.consumer.dlx", "order.release.dlq");
        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .messageId("msg-1")
                .correlationId("corr-1")
                .build();
        return new GetResponse(envelope, props, body.getBytes(StandardCharsets.UTF_8), 0);
    }
}

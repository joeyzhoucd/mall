package com.mall.mq.service;

import com.mall.common.constant.MqConstants;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.LongString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * 死信队列控制台。
 *
 * <h3>2026-09-06：peek 和 replay 改成手工确认，原来会丢消息</h3>
 * 原实现是 {@code rabbitTemplate.receive(dlq)} 然后再 {@code send(...)}。
 * {@code receive()} 是<b>自动确认</b>的 —— 它一返回，消息在 broker 上就没了。
 * 于是：
 * <ul>
 *   <li><b>replay</b>：如果紧接着的 send 抛异常（channel 被关、交换机被删、
 *       网络抖动），这条消息既没回到源队列、也不在死信队列里了，<b>永久丢失</b>。
 *       而死信队列存在的全部意义就是「不丢」。</li>
 *   <li><b>peek</b>：收到之后再 {@code send("", dlq, message)} 放回去，
 *       放回的位置是<b>队尾</b>。也就是说「看一眼」会改变队列顺序；
 *       而且中间同样有一个丢失窗口。</li>
 * </ul>
 * 现在两个都走 {@code basicGet(queue, false)}（不自动确认）：
 * <ul>
 *   <li>replay：先 publish 再 ack；publish 失败就 nack 并 requeue。</li>
 *   <li>peek：拿到就 nack + requeue，消息回到<b>队首</b>，顺序不变。</li>
 * </ul>
 *
 * <h3>剩下的那个窗口：可能重复，但不会丢</h3>
 * publish 成功、ack 之前进程崩溃的话，这条消息会既进了源队列、又还在死信队列里，
 * 下次 replay 会再投一遍。这是<b>刻意选的</b>方向 ——
 * 重复由消费幂等表（oms_mq_consume_message / wms_mq_consume_message）挡住，
 * 那张表就是为这件事存在的；而丢失没有任何东西能补救。
 *
 * <h3>为什么不用 basicPublish 之外的写法</h3>
 * {@code basicGet} 返回的 {@code GetResponse} 里已经是原始的
 * {@code AMQP.BasicProperties}，{@code basicPublish} 要的也正是这个类型，
 * 中间不需要经过 Spring 的 MessageProperties 转换 ——
 * 少一次转换就少一处「某个头字段在往返中丢了」的可能。
 */
@Service
public class MqDlqService {

    private static final Logger log = LoggerFactory.getLogger(MqDlqService.class);

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    /** 消息体展示上限。死信里可能是一个很大的 payload，整条塞进响应没有意义。 */
    private static final int BODY_PREVIEW_LIMIT = 1000;

    /** 丢弃时写进日志的消息体长度。留痕用，不需要完整。 */
    private static final int DISCARD_LOG_LIMIT = 200;

    private final RabbitTemplate rabbitTemplate;
    private final RabbitAdmin rabbitAdmin;

    public MqDlqService(RabbitTemplate rabbitTemplate, RabbitAdmin rabbitAdmin) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitAdmin = rabbitAdmin;
    }

    public List<DlqQueueView> overview() {
        List<DlqQueueView> result = new ArrayList<>();
        for (DlqBinding binding : bindings()) {
            Properties properties = rabbitAdmin.getQueueProperties(binding.dlq());
            int count = properties == null || properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT) == null
                    ? 0
                    : ((Number) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).intValue();
            result.add(new DlqQueueView(
                    binding.sourceQueue(),
                    binding.dlq(),
                    binding.replayExchange(),
                    binding.replayRoutingKey(),
                    count
            ));
        }
        return result;
    }

    /**
     * 看队首的一条，<b>不消费</b>。
     *
     * <p>拿到之后立刻 nack + requeue，消息回到队首，队列顺序和深度都不变。
     * AMQP 没有真正的"只读"取消息原语（basicGet 一定会把消息交出来），
     * 所以"不改变状态"这件事只能靠不确认来实现。
     */
    public DlqMessageView peek(String dlq) {
        DlqBinding binding = requireBinding(dlq);
        return rabbitTemplate.execute(channel -> {
            GetResponse response = channel.basicGet(binding.dlq(), false);
            if (response == null) {
                return null;
            }
            // requeue=true：放回去，而且是放回【队首】，不像原来那样挪到队尾。
            channel.basicNack(response.getEnvelope().getDeliveryTag(), false, true);
            return view(binding, response);
        });
    }

    /**
     * 把死信重新投回源交换机。
     *
     * <p>逐条：publish 成功才 ack；publish 失败就 nack + requeue 并中止，
     * 已经成功的那些保持已投递。返回真正重投成功的条数。
     */
    public int replay(String dlq, Integer limit) {
        DlqBinding binding = requireBinding(dlq);
        int count = normalizedLimit(limit);
        Integer replayed = rabbitTemplate.execute(channel -> {
            int done = 0;
            for (int i = 0; i < count; i++) {
                GetResponse response = channel.basicGet(binding.dlq(), false);
                if (response == null) {
                    break;
                }
                long deliveryTag = response.getEnvelope().getDeliveryTag();
                try {
                    channel.basicPublish(binding.replayExchange(), binding.replayRoutingKey(),
                            response.getProps(), response.getBody());
                    channel.basicAck(deliveryTag, false);
                    done++;
                } catch (Exception e) {
                    // 投递失败：把它放回死信队列，绝不能就这么 ack 掉。
                    channel.basicNack(deliveryTag, false, true);
                    log.error("死信重投失败，已放回 {}：已成功重投 {} 条", binding.dlq(), done, e);
                    throw e;
                }
            }
            return done;
        });
        return replayed == null ? 0 : replayed;
    }

    /**
     * 丢弃死信。<b>不可恢复</b>。
     *
     * <p>这里用自动确认是对的 —— 目的就是让消息消失。
     * 但丢弃前会把 messageId 和消息体前 {@value #DISCARD_LOG_LIMIT} 个字符写进日志：
     * 一个不可逆的操作如果连痕迹都不留，事后就完全无法回答
     * 「当时丢掉的是什么」。日志不能撤销操作，但至少能重建事实。
     */
    public int discard(String dlq, Integer limit) {
        DlqBinding binding = requireBinding(dlq);
        int count = normalizedLimit(limit);
        int discarded = 0;
        for (int i = 0; i < count; i++) {
            Message message = rabbitTemplate.receive(binding.dlq());
            if (message == null) {
                break;
            }
            discarded++;
            log.warn("丢弃死信 dlq={} messageId={} body={}",
                    binding.dlq(),
                    message.getMessageProperties().getMessageId(),
                    preview(message.getBody(), DISCARD_LOG_LIMIT));
        }
        if (discarded > 0) {
            log.warn("共丢弃 {} 条死信，队列 {}，此操作不可恢复", discarded, binding.dlq());
        }
        return discarded;
    }

    private DlqMessageView view(DlqBinding binding, GetResponse response) {
        return new DlqMessageView(
                binding.sourceQueue(),
                binding.dlq(),
                response.getProps().getMessageId(),
                response.getProps().getCorrelationId(),
                response.getEnvelope().getRoutingKey(),
                response.getEnvelope().getExchange(),
                normalizeHeaders(response.getProps().getHeaders()),
                preview(response.getBody(), BODY_PREVIEW_LIMIT)
        );
    }

    /**
     * 把 amqp-client 的头字段转成能直接 JSON 序列化的形式。
     *
     * <p>必须做这一步：驱动把字符串包成 {@link LongString}，
     * Jackson 序列化它时输出的是那个对象的内部字段（bytes 之类），
     * 而不是字符串本身 —— 界面上会看到一堆无意义的结构。
     *
     * <p>递归处理是因为最有价值的那个头 {@code x-death} 本身是
     * {@code List<Map<String, Object>>}，里面嵌着 LongString。
     * 它记录了「因为什么原因、被拒过几次、原队列是哪个」，
     * 是死信页面上唯一能回答"为什么进来的"的东西，不能糊掉。
     */
    static Object normalizeHeaders(Object value) {
        if (value instanceof LongString longString) {
            return longString.toString();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalizeHeaders(entry.getValue()));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(normalizeHeaders(item));
            }
            return result;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return Map.of();
        }
        return (Map<String, Object>) normalizeHeaders((Object) headers);
    }

    private static String preview(byte[] body, int limit) {
        if (body == null) {
            return "";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private DlqBinding requireBinding(String dlq) {
        for (DlqBinding binding : bindings()) {
            if (binding.dlq().equals(dlq)) {
                return binding;
            }
        }
        // 白名单之外的队列名一律拒绝：这几个接口能收发任意队列的消息，
        // 让调用方自由指定队列名等于把 broker 的读写权限暴露给后台。
        throw new IllegalArgumentException("unsupported dlq: " + dlq);
    }

    private int normalizedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public static List<DlqBinding> bindings() {
        return List.of(
                new DlqBinding(MqConstants.ORDER_RELEASE_QUEUE, MqConstants.ORDER_RELEASE_DLQ,
                        MqConstants.ORDER_EVENT_EXCHANGE, MqConstants.ORDER_RELEASE_ROUTING_KEY),
                new DlqBinding(MqConstants.SECKILL_ORDER_QUEUE, MqConstants.SECKILL_ORDER_DLQ,
                        MqConstants.SECKILL_EVENT_EXCHANGE, MqConstants.SECKILL_ORDER_ROUTING_KEY),
                new DlqBinding(MqConstants.STOCK_RELEASE_QUEUE, MqConstants.STOCK_RELEASE_DLQ,
                        MqConstants.STOCK_RELEASE_EXCHANGE, MqConstants.STOCK_RELEASE_ROUTING_KEY),
                new DlqBinding(MqConstants.STOCK_DEDUCT_QUEUE, MqConstants.STOCK_DEDUCT_DLQ,
                        MqConstants.STOCK_RELEASE_EXCHANGE, MqConstants.STOCK_DEDUCT_ROUTING_KEY),
                new DlqBinding(MqConstants.STOCK_FAIL_QUEUE, MqConstants.STOCK_FAIL_DLQ,
                        MqConstants.STOCK_RELEASE_EXCHANGE, MqConstants.STOCK_FAIL_ROUTING_KEY)
        );
    }

    public record DlqBinding(String sourceQueue, String dlq, String replayExchange, String replayRoutingKey) {
    }

    public record DlqQueueView(String sourceQueue, String dlq, String replayExchange, String replayRoutingKey,
                               int messageCount) {
    }

    public record DlqMessageView(String sourceQueue, String dlq, String messageId, String correlationId,
                                 String routingKey, String exchange, Map<String, Object> headers, String body) {
    }
}

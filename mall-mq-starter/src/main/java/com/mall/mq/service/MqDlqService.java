package com.mall.mq.service;

import com.mall.common.constant.MqConstants;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Service
public class MqDlqService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

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

    public DlqMessageView peek(String dlq) {
        DlqBinding binding = requireBinding(dlq);
        Message message = rabbitTemplate.receive(binding.dlq());
        if (message == null) {
            return null;
        }
        rabbitTemplate.send("", binding.dlq(), message);
        return view(binding, message);
    }

    public int replay(String dlq, Integer limit) {
        DlqBinding binding = requireBinding(dlq);
        int count = normalizedLimit(limit);
        int replayed = 0;
        for (int i = 0; i < count; i++) {
            Message message = rabbitTemplate.receive(binding.dlq());
            if (message == null) {
                break;
            }
            rabbitTemplate.send(binding.replayExchange(), binding.replayRoutingKey(), message);
            replayed++;
        }
        return replayed;
    }

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
        }
        return discarded;
    }

    private DlqMessageView view(DlqBinding binding, Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        if (body.length() > 1000) {
            body = body.substring(0, 1000);
        }
        return new DlqMessageView(
                binding.sourceQueue(),
                binding.dlq(),
                message.getMessageProperties().getMessageId(),
                message.getMessageProperties().getCorrelationId(),
                message.getMessageProperties().getReceivedRoutingKey(),
                message.getMessageProperties().getReceivedExchange(),
                message.getMessageProperties().getHeaders(),
                body
        );
    }

    private DlqBinding requireBinding(String dlq) {
        for (DlqBinding binding : bindings()) {
            if (binding.dlq().equals(dlq)) {
                return binding;
            }
        }
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

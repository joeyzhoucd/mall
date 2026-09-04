package com.mall.order.config;

import com.mall.order.service.OrderOutboxMessageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "mall.order.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderOutboxRabbitConfig {

    public OrderOutboxRabbitConfig(RabbitTemplate rabbitTemplate,
                                   OrderOutboxMessageService orderOutboxMessageService) {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            Long id = parseId(correlationData);
            if (id == null) {
                return;
            }
            if (ack) {
                orderOutboxMessageService.markSent(id);
            } else {
                orderOutboxMessageService.markFailed(id, StringUtils.defaultIfBlank(cause, "broker nack"));
            }
        });
        rabbitTemplate.setReturnsCallback((ReturnedMessage returned) -> {
            Long id = null;
            if (returned != null && returned.getMessage() != null) {
                id = parseId(returned.getMessage().getMessageProperties().getCorrelationId());
            }
            if (id != null) {
                orderOutboxMessageService.markFailed(id, returned.getReplyText());
            }
        });
    }

    private static Long parseId(CorrelationData correlationData) {
        if (correlationData == null || StringUtils.isBlank(correlationData.getId())) {
            return null;
        }
        try {
            return Long.valueOf(correlationData.getId());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseId(String id) {
        if (StringUtils.isBlank(id)) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}

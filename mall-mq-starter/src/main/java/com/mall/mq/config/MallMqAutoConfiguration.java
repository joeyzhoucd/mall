package com.mall.mq.config;

import com.mall.common.constant.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MallMqProperties.class)
public class MallMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    @ConditionalOnMissingBean(ConnectionFactory.class)
    public ConnectionFactory connectionFactory(MallMqProperties properties) {
        MallMqProperties.Connection connection = properties.getConnection();
        CachingConnectionFactory factory = new CachingConnectionFactory(connection.getHost(), connection.getPort());
        factory.setUsername(connection.getUsername());
        factory.setPassword(connection.getPassword());
        // 秒杀抢购要在返回用户"成功"之前等 broker 真正确认收到消息（publisher confirm），
        // 光靠 convertAndSend 不出异常不代表消息真的落进了 broker——网络抖动/broker 短暂
        // 拒收都不会在调用侧直接抛异常。这里对所有服务统一开启，其他现有的订单/库存
        // 队列不使用 CorrelationData 就不会受影响，开着也没有额外成本。
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        return factory;
    }

    @Configuration
    @ConditionalOnProperty(prefix = "mall.mq.order", name = "enabled", havingValue = "true")
    public static class OrderMqConfiguration {

        @Bean
        public Exchange orderEventExchange() {
            return ExchangeBuilder.topicExchange(MqConstants.ORDER_EVENT_EXCHANGE).durable(true).build();
        }

        @Bean
        public Queue orderDelayQueue(MallMqProperties properties) {
            return QueueBuilder.durable(MqConstants.ORDER_DELAY_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.ORDER_EVENT_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.ORDER_RELEASE_ROUTING_KEY)
                    .withArgument("x-message-ttl", properties.getOrder().getDelayTtlMs())
                    .build();
        }

        @Bean
        public Queue orderReleaseQueue() {
            return QueueBuilder.durable(MqConstants.ORDER_RELEASE_QUEUE).build();
        }

        @Bean
        public Binding orderCreateBinding(Queue orderDelayQueue, Exchange orderEventExchange) {
            return BindingBuilder.bind(orderDelayQueue).to(orderEventExchange)
                    .with(MqConstants.ORDER_CREATE_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding orderReleaseBinding(Queue orderReleaseQueue, Exchange orderEventExchange) {
            return BindingBuilder.bind(orderReleaseQueue).to(orderEventExchange)
                    .with(MqConstants.ORDER_RELEASE_ROUTING_KEY).noargs();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "mall.mq.stock", name = "enabled", havingValue = "true")
    public static class StockMqConfiguration {

        @Bean
        public Exchange stockReleaseExchange() {
            return ExchangeBuilder.topicExchange(MqConstants.STOCK_RELEASE_EXCHANGE).durable(true).build();
        }

        @Bean
        public Queue stockReleaseQueue() {
            return QueueBuilder.durable(MqConstants.STOCK_RELEASE_QUEUE).build();
        }

        @Bean
        public Queue stockDeductQueue() {
            return QueueBuilder.durable(MqConstants.STOCK_DEDUCT_QUEUE).build();
        }

        @Bean
        public Queue stockFailQueue() {
            return QueueBuilder.durable(MqConstants.STOCK_FAIL_QUEUE).build();
        }

        @Bean
        public Binding stockReleaseBinding(Queue stockReleaseQueue, Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockReleaseQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_RELEASE_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockDeductBinding(Queue stockDeductQueue, Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockDeductQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_DEDUCT_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockFailBinding(Queue stockFailQueue, Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockFailQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_FAIL_ROUTING_KEY).noargs();
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "mall.mq.seckill", name = "enabled", havingValue = "true")
    public static class SeckillMqConfiguration {

        @Bean
        public Exchange seckillEventExchange() {
            return ExchangeBuilder.topicExchange(MqConstants.SECKILL_EVENT_EXCHANGE).durable(true).build();
        }

        @Bean
        public Queue seckillOrderQueue() {
            return QueueBuilder.durable(MqConstants.SECKILL_ORDER_QUEUE).build();
        }

        @Bean
        public Binding seckillOrderBinding(Queue seckillOrderQueue, Exchange seckillEventExchange) {
            return BindingBuilder.bind(seckillOrderQueue).to(seckillEventExchange)
                    .with(MqConstants.SECKILL_ORDER_ROUTING_KEY).noargs();
        }
    }
}


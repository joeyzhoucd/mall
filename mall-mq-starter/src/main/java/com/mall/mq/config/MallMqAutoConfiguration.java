package com.mall.mq.config;

import com.mall.common.constant.MqConstants;
import com.mall.mq.controller.MqDlqController;
import com.mall.mq.service.MqDlqService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MallMqProperties.class)
@Import({MqDlqService.class, MqDlqController.class})
/**
 * MQ 的交换机、队列、绑定关系。
 *
 * <h3>为什么每个参数都显式写了 @Qualifier</h3>
 * 这些 @Bean 方法要注入 Queue / Exchange，而同一个配置类里有多个同类型的 bean。
 * Spring 在有多个候选时会退化到【按参数名匹配 bean 名】，而参数名要靠编译时的
 * -parameters 选项写进 class 文件才拿得到。也就是说不加 @Qualifier 的话，
 * 这份配置能不能装配成功取决于一个【编译选项】。
 *
 * 这不是理论担忧：实测出现过「本地构建的 class 有参数名、CI 构建的没有」，
 * 于是同一份代码本地起得来、集群里起不来，报错是
 *   Parameter 0 of method stockReleaseBinding required a single bean, but 3 were found
 * 而且 Spring 那段报错里关于 -parameters 的提示是【无条件输出】的
 * （反汇编 NoUniqueBeanDefinitionFailureAnalyzer 确认过：ldc 到 append 之间没有分支），
 * 所以看到那句话并不能说明参数名真的缺失 —— 它会把排查带向错误的方向。
 *
 * 显式 @Qualifier 让装配结果只依赖源码本身，不依赖编译选项、也不依赖参数名保留。
 * 这也是 Spring 报错里给出的第一个建议。-parameters 该开还是要开（它对别处有用），
 * 但正确性不应该建立在它一定生效之上。
 */
public class MallMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MessageConverter messageConverter() {
        // 同样的规律：类名不带 2 的才是 Jackson 3 实现。MQ 消息的序列化如果还用带 2 的，
        // 发消息时会因为 Jackson 2 不在 classpath 上而失败。
        return new JacksonJsonMessageConverter();
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

    @Bean
    @ConditionalOnMissingBean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean(name = "rabbitListenerContainerFactory")
    @ConditionalOnMissingBean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                              MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public Exchange consumerDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE).durable(true).build();
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
            return QueueBuilder.durable(MqConstants.ORDER_RELEASE_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.ORDER_RELEASE_DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public Queue orderReleaseDlq() {
            return QueueBuilder.durable(MqConstants.ORDER_RELEASE_DLQ).build();
        }

        @Bean
        public Binding orderCreateBinding(@Qualifier("orderDelayQueue") Queue orderDelayQueue,
                                          @Qualifier("orderEventExchange") Exchange orderEventExchange) {
            return BindingBuilder.bind(orderDelayQueue).to(orderEventExchange)
                    .with(MqConstants.ORDER_CREATE_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding orderReleaseBinding(@Qualifier("orderReleaseQueue") Queue orderReleaseQueue,
                                          @Qualifier("orderEventExchange") Exchange orderEventExchange) {
            return BindingBuilder.bind(orderReleaseQueue).to(orderEventExchange)
                    .with(MqConstants.ORDER_RELEASE_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding orderReleaseDlqBinding(@Qualifier("orderReleaseDlq") Queue orderReleaseDlq,
                                             @Qualifier("consumerDeadLetterExchange") Exchange consumerDeadLetterExchange) {
            return BindingBuilder.bind(orderReleaseDlq).to(consumerDeadLetterExchange)
                    .with(MqConstants.ORDER_RELEASE_DLQ_ROUTING_KEY).noargs();
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
            return QueueBuilder.durable(MqConstants.STOCK_RELEASE_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.STOCK_RELEASE_DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public Queue stockDeductQueue() {
            return QueueBuilder.durable(MqConstants.STOCK_DEDUCT_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.STOCK_DEDUCT_DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public Queue stockFailQueue() {
            return QueueBuilder.durable(MqConstants.STOCK_FAIL_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.STOCK_FAIL_DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public Queue stockReleaseDlq() {
            return QueueBuilder.durable(MqConstants.STOCK_RELEASE_DLQ).build();
        }

        @Bean
        public Queue stockDeductDlq() {
            return QueueBuilder.durable(MqConstants.STOCK_DEDUCT_DLQ).build();
        }

        @Bean
        public Queue stockFailDlq() {
            return QueueBuilder.durable(MqConstants.STOCK_FAIL_DLQ).build();
        }

        @Bean
        public Binding stockReleaseBinding(@Qualifier("stockReleaseQueue") Queue stockReleaseQueue,
                                          @Qualifier("stockReleaseExchange") Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockReleaseQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_RELEASE_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockDeductBinding(@Qualifier("stockDeductQueue") Queue stockDeductQueue,
                                          @Qualifier("stockReleaseExchange") Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockDeductQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_DEDUCT_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockFailBinding(@Qualifier("stockFailQueue") Queue stockFailQueue,
                                          @Qualifier("stockReleaseExchange") Exchange stockReleaseExchange) {
            return BindingBuilder.bind(stockFailQueue).to(stockReleaseExchange)
                    .with(MqConstants.STOCK_FAIL_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockReleaseDlqBinding(@Qualifier("stockReleaseDlq") Queue stockReleaseDlq,
                                             @Qualifier("consumerDeadLetterExchange") Exchange consumerDeadLetterExchange) {
            return BindingBuilder.bind(stockReleaseDlq).to(consumerDeadLetterExchange)
                    .with(MqConstants.STOCK_RELEASE_DLQ_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockDeductDlqBinding(@Qualifier("stockDeductDlq") Queue stockDeductDlq,
                                            @Qualifier("consumerDeadLetterExchange") Exchange consumerDeadLetterExchange) {
            return BindingBuilder.bind(stockDeductDlq).to(consumerDeadLetterExchange)
                    .with(MqConstants.STOCK_DEDUCT_DLQ_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding stockFailDlqBinding(@Qualifier("stockFailDlq") Queue stockFailDlq,
                                          @Qualifier("consumerDeadLetterExchange") Exchange consumerDeadLetterExchange) {
            return BindingBuilder.bind(stockFailDlq).to(consumerDeadLetterExchange)
                    .with(MqConstants.STOCK_FAIL_DLQ_ROUTING_KEY).noargs();
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
            return QueueBuilder.durable(MqConstants.SECKILL_ORDER_QUEUE)
                    .withArgument("x-dead-letter-exchange", MqConstants.CONSUMER_DEAD_LETTER_EXCHANGE)
                    .withArgument("x-dead-letter-routing-key", MqConstants.SECKILL_ORDER_DLQ_ROUTING_KEY)
                    .build();
        }

        @Bean
        public Queue seckillOrderDlq() {
            return QueueBuilder.durable(MqConstants.SECKILL_ORDER_DLQ).build();
        }

        @Bean
        public Binding seckillOrderBinding(@Qualifier("seckillOrderQueue") Queue seckillOrderQueue,
                                          @Qualifier("seckillEventExchange") Exchange seckillEventExchange) {
            return BindingBuilder.bind(seckillOrderQueue).to(seckillEventExchange)
                    .with(MqConstants.SECKILL_ORDER_ROUTING_KEY).noargs();
        }

        @Bean
        public Binding seckillOrderDlqBinding(@Qualifier("seckillOrderDlq") Queue seckillOrderDlq,
                                             @Qualifier("consumerDeadLetterExchange") Exchange consumerDeadLetterExchange) {
            return BindingBuilder.bind(seckillOrderDlq).to(consumerDeadLetterExchange)
                    .with(MqConstants.SECKILL_ORDER_DLQ_ROUTING_KEY).noargs();
        }
    }
}

package com.mall.coupon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * activate() 重新激活某场秒杀时，要通知所有 mall-coupon 副本清掉各自进程内的本地
 * "已售罄"标记（SeckillGrabServiceImpl.localSoldOutUntil）——那份标记只存在每个 pod
 * 自己的内存里，互相看不到，activate() 直接调用只能清掉处理这次请求的那一个 pod。
 * 用 Redis Pub/Sub 广播一下，让所有订阅了这个频道的 pod（包括发广播的那个自己）
 * 都能立刻响应，不用像之前那样只能靠一个固定 TTL 硬等。
 */
@Configuration
public class SeckillPubSubConfig {

    public static final String ACTIVATE_CHANNEL = "seckill:activate:channel";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, SeckillActivateListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(ACTIVATE_CHANNEL));
        return container;
    }
}

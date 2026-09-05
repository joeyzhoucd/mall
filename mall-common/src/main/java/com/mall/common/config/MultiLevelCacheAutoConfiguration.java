package com.mall.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@AutoConfiguration
@ConditionalOnClass(Caffeine.class)
@EnableConfigurationProperties(MultiLevelCacheProperties.class)
public class MultiLevelCacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheAutoConfiguration.class);

    @ConditionalOnClass(StringRedisTemplate.class)
    static class RedisMultiLevelCacheConfiguration {

        @Bean
        @ConditionalOnMissingBean
        MultiLevelCacheClient multiLevelCacheClient(StringRedisTemplate redisTemplate,
                                                    ObjectMapper objectMapper,
                                                    ObjectProvider<MeterRegistry> registries,
                                                    MultiLevelCacheProperties properties) {
            MeterRegistry registry = registries.getIfAvailable(() -> {
                log.warn("No MeterRegistry found; MultiLevelCacheClient metrics will use an unexported registry.");
                return new SimpleMeterRegistry();
            });
            return new MultiLevelCacheClient(redisTemplate, objectMapper, registry, properties);
        }

        @Bean
        RedisMessageListenerContainer multiLevelCacheInvalidationListenerContainer(
                StringRedisTemplate redisTemplate,
                MultiLevelCacheClient cacheClient,
                MultiLevelCacheProperties properties) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisTemplate.getConnectionFactory());
            container.setAutoStartup(properties.enabled());
            container.addMessageListener((message, pattern) -> {
                String fullKey = new String(message.getBody(), StandardCharsets.UTF_8);
                cacheClient.invalidateLocal(fullKey);
            }, new ChannelTopic(properties.invalidationChannel()));
            return container;
        }
    }
}

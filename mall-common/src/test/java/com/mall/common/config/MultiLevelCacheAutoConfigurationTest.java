package com.mall.common.config;

import com.mall.common.cache.MultiLevelCacheClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiLevelCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MultiLevelCacheAutoConfiguration.class))
            .withBean(StringRedisTemplate.class, MultiLevelCacheAutoConfigurationTest::redisTemplate)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void createsClientWhenRedisAndJacksonAreAvailable() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MultiLevelCacheClient.class);
                });
    }

    @Test
    void createsClientEvenWithoutMeterRegistry() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(MultiLevelCacheClient.class);
        });
    }

    private static StringRedisTemplate redisTemplate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.getConnectionFactory()).thenReturn(mock(RedisConnectionFactory.class));
        return redisTemplate;
    }
}

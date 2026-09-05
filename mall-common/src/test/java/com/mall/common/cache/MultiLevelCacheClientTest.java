package com.mall.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiLevelCacheClientTest {

    @Test
    void localHitUsesInjectedObjectMapper() throws Exception {
        StringRedisTemplate redis = mockRedis();
        ObjectMapper mapper = mock(ObjectMapper.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MultiLevelCacheClient client = new MultiLevelCacheClient(redis, mapper, registry, properties(100));
        TypeReference<Payload> type = new TypeReference<>() {
        };
        Payload payload = new Payload(42L);

        when(mapper.writeValueAsString(payload)).thenReturn("{\"id\":42}");
        when(mapper.readValue("{\"id\":42}", type)).thenReturn(payload);

        client.put("sku", "42", payload, options());
        Payload actual = client.get("sku", "42", type, () -> {
            throw new AssertionError("loader should not run on local hit");
        }, options());

        assertThat(actual).isSameAs(payload);
        verify(mapper).readValue("{\"id\":42}", type);
    }

    @Test
    void hotKeyMetricIsReportedOncePerWindow() throws Exception {
        StringRedisTemplate redis = mockRedis();
        ObjectMapper mapper = mock(ObjectMapper.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MultiLevelCacheClient client = new MultiLevelCacheClient(redis, mapper, registry, properties(2));
        TypeReference<Payload> type = new TypeReference<>() {
        };
        AtomicInteger loads = new AtomicInteger();

        when(mapper.writeValueAsString(any())).thenReturn("{\"id\":1}");
        when(mapper.readValue("{\"id\":1}", type)).thenReturn(new Payload(1L));

        for (int i = 0; i < 5; i++) {
            client.get("sku", "hot", type, () -> {
                loads.incrementAndGet();
                return new Payload(1L);
            }, options());
        }

        assertThat(loads).hasValue(1);
        assertThat(registry.find("mall.cache.hot.key").tag("cache", "sku").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void evictDeletesRedisAndBroadcastsLocalInvalidation() {
        StringRedisTemplate redis = mockRedis();
        MultiLevelCacheClient client = new MultiLevelCacheClient(
                redis, new ObjectMapper(), new SimpleMeterRegistry(), properties(100));

        client.evict("sku", "42");

        verify(redis).delete("mall:cache:sku:42");
        verify(redis).convertAndSend("mall:cache:multi-level:invalidate", "mall:cache:sku:42");
    }

    @SuppressWarnings("unchecked")
    private static StringRedisTemplate mockRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        return redis;
    }

    private static MultiLevelCacheProperties properties(long hotKeyThreshold) {
        return new MultiLevelCacheProperties(
                true,
                "mall:cache",
                "mall:cache:multi-level:invalidate",
                new MultiLevelCacheProperties.Local(100),
                new MultiLevelCacheProperties.HotKey(Duration.ofSeconds(30), hotKeyThreshold));
    }

    private static MultiLevelCacheOptions options() {
        return new MultiLevelCacheOptions(
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                true,
                Duration.ofSeconds(1),
                Duration.ZERO,
                Duration.ZERO,
                0);
    }

    private record Payload(Long id) {
    }
}

package com.mall.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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

    /**
     * 回源写回的 key 必须就是读的 key —— 缓存名 / key 里带冒号也一样。
     *
     * <p>2026-09-28 发现：回源后曾用「去前缀、在第一个冒号处切」反解析 key，而项目里几乎所有缓存名都带冒号
     * （product:sku-item、coupon:seckill-page、ware:sku-available-stock…），写到了
     * {@code mall:cache:product:sku-item:sku-item:42}，读的是 {@code mall:cache:product:sku-item:42}，
     * 自 2026-09-05 起全部缓存从未命中。上面那条热点测试用的缓存名是 {@code sku}（不带冒号），所以一直是绿的。
     *
     * <p>Redis 用一个 Map 模拟真实读写，两个 client 代表两个 pod：只看本地缓存测不出来，
     * 因为本地缓存也写到了同一个错 key 上；共享那一层（B 读到 A 写的）才是缓存存在的意义。
     */
    @ParameterizedTest
    @CsvSource({"sku,42", "product:sku-item,42", "sku,6:42", "reco:complement,6:42"})
    void loadedValueIsStoredUnderTheKeyItWasReadFrom(String cacheName, String key) {
        Map<String, String> store = new ConcurrentHashMap<>();
        MultiLevelCacheClient podA = new MultiLevelCacheClient(mapRedis(store), new ObjectMapper(), new SimpleMeterRegistry(), properties(100));
        MultiLevelCacheClient podB = new MultiLevelCacheClient(mapRedis(store), new ObjectMapper(), new SimpleMeterRegistry(), properties(100));
        TypeReference<Payload> type = new TypeReference<>() {
        };
        AtomicInteger loads = new AtomicInteger();
        Supplier<Payload> loader = () -> {
            loads.incrementAndGet();
            return new Payload(42L);
        };

        assertThat(podA.get(cacheName, key, type, loader, options())).isEqualTo(new Payload(42L));
        assertThat(store).containsKey("mall:cache:" + cacheName + ":" + key);
        assertThat(podA.get(cacheName, key, type, loader, options())).as("本 pod 第二次读：本地命中").isEqualTo(new Payload(42L));
        assertThat(podB.get(cacheName, key, type, loader, options())).as("另一个 pod：Redis 命中").isEqualTo(new Payload(42L));
        assertThat(loads).as("三次读只回源一次").hasValue(1);
    }

    /** 别的 pod 改了值并广播失效：收到之前本地还是旧的，收到之后读到新的 */
    @Test
    void invalidationMessageDropsTheLocalCopy() {
        Map<String, String> store = new ConcurrentHashMap<>();
        MultiLevelCacheClient pod = new MultiLevelCacheClient(mapRedis(store), new ObjectMapper(), new SimpleMeterRegistry(), properties(100));
        MultiLevelCacheInvalidationListener listener = new MultiLevelCacheInvalidationListener(pod);
        TypeReference<Payload> type = new TypeReference<>() {
        };
        String fullKey = "mall:cache:product:sku-item:42";
        store.put(fullKey, "{\"id\":1}");
        assertThat(pod.get("product:sku-item", "42", type, () -> null, options())).isEqualTo(new Payload(1L));

        store.put(fullKey, "{\"id\":2}");                      // 另一个 pod 更新了 Redis
        assertThat(pod.get("product:sku-item", "42", type, () -> null, options())).as("本地副本还在").isEqualTo(new Payload(1L));

        listener.onMessage(new DefaultMessage("mall:cache:multi-level:invalidate".getBytes(StandardCharsets.UTF_8),
                fullKey.getBytes(StandardCharsets.UTF_8)), null);
        assertThat(pod.get("product:sku-item", "42", type, () -> null, options())).as("失效后读到新值").isEqualTo(new Payload(2L));
    }

    /** 抢不到锁、等到别人写好的那条路径，读的也必须是同一个 key */
    @Test
    void waiterReadsTheValueTheLockHolderWrote() {
        Map<String, String> store = new ConcurrentHashMap<>();
        store.put("mall:cache:product:sku-item:42:lock", "someone-else");
        StringRedisTemplate redis = mapRedis(store);
        MultiLevelCacheClient client = new MultiLevelCacheClient(redis, new ObjectMapper(), new SimpleMeterRegistry(), properties(100));
        MultiLevelCacheOptions waitOptions = new MultiLevelCacheOptions(Duration.ofMinutes(1), Duration.ofMinutes(5),
                Duration.ofSeconds(30), true, Duration.ofSeconds(1), Duration.ofMillis(200), Duration.ofMillis(10), 0);
        // 持锁的那个 pod 在我们等待期间写好了值
        new Thread(() -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException ignored) {
            }
            store.put("mall:cache:product:sku-item:42", "{\"id\":7}");
        }).start();

        Payload got = client.get("product:sku-item", "42", new TypeReference<>() {
        }, () -> {
            throw new AssertionError("应当读到持锁者写的值，而不是自己回源");
        }, waitOptions);
        assertThat(got).isEqualTo(new Payload(7L));
    }

    /** 用 Map 当 Redis：get / set / setIfAbsent / delete 按真实语义读写同一份数据（不模拟过期） */
    @SuppressWarnings("unchecked")
    private static StringRedisTemplate mapRedis(Map<String, String> store) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> store.get(i.<String>getArgument(0)));
        doAnswer(i -> store.put(i.getArgument(0), i.getArgument(1))).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(i -> store.putIfAbsent(i.getArgument(0), i.getArgument(1)) == null);
        // 解锁脚本：比对 token 后删锁
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(i -> {
            List<String> keys = i.getArgument(1);
            Object token = i.getArgument(2);
            return store.remove(keys.get(0), token) ? 1L : 0L;
        });
        return redis;
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

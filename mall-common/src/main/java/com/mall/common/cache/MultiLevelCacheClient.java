package com.mall.common.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class MultiLevelCacheClient {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCacheClient.class);
    private static final String NULL_VALUE = "__NULL__";
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            else
              return 0
            end
            """;

    private final Cache<String, LocalValue> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final MultiLevelCacheProperties properties;
    private final ConcurrentHashMap<String, HotKeyWindow> hotKeyWindows = new ConcurrentHashMap<>();
    private final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    public MultiLevelCacheClient(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry,
                                 MultiLevelCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.properties = properties;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(properties.local().maximumSize())
                .recordStats()
                .build();
    }

    public <T> T get(String cacheName,
                     String key,
                     TypeReference<T> typeReference,
                     Supplier<T> loader,
                     MultiLevelCacheOptions options) {
        if (!properties.enabled()) {
            return loader.get();
        }
        Objects.requireNonNull(typeReference, "typeReference");
        Objects.requireNonNull(loader, "loader");
        MultiLevelCacheOptions effective = options == null ? MultiLevelCacheOptions.defaults() : options;
        String fullKey = fullKey(cacheName, key);
        recordHotKey(cacheName, fullKey);

        LocalValue local = localCache.getIfPresent(fullKey);
        if (local != null && !local.expired()) {
            record(cacheName, local.nullValue() ? "local_null_hit" : "local_hit");
            return local.nullValue() ? null : read(local.body(), typeReference);
        }

        String remote = redisTemplate.opsForValue().get(fullKey);
        if (remote != null) {
            boolean nullValue = NULL_VALUE.equals(remote);
            localCache.put(fullKey, LocalValue.of(remote, nullValue, effective.localTtl()));
            record(cacheName, nullValue ? "redis_null_hit" : "redis_hit");
            return nullValue ? null : read(remote, typeReference);
        }

        record(cacheName, "miss");
        return loadWithMutex(cacheName, fullKey, typeReference, loader, effective);
    }

    public void put(String cacheName, String key, Object value, MultiLevelCacheOptions options) {
        if (!properties.enabled()) {
            return;
        }
        MultiLevelCacheOptions effective = options == null ? MultiLevelCacheOptions.defaults() : options;
        String fullKey = fullKey(cacheName, key);
        String body = value == null ? NULL_VALUE : write(value);
        Duration remoteTtl = ttlWithJitter(effective.redisTtl(), effective.jitterRatio());
        redisTemplate.opsForValue().set(fullKey, body, remoteTtl);
        localCache.put(fullKey, LocalValue.of(body, value == null, effective.localTtl()));
        record(cacheName, value == null ? "put_null" : "put");
    }

    public void evict(String cacheName, String key) {
        String fullKey = fullKey(cacheName, key);
        localCache.invalidate(fullKey);
        redisTemplate.delete(fullKey);
        redisTemplate.convertAndSend(properties.invalidationChannel(), fullKey);
        record(cacheName, "evict");
    }

    public void invalidateLocal(String fullKey) {
        localCache.invalidate(fullKey);
    }

    private <T> T loadWithMutex(String cacheName,
                                String fullKey,
                                TypeReference<T> typeReference,
                                Supplier<T> loader,
                                MultiLevelCacheOptions options) {
        String lockKey = fullKey + ":lock";
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, options.lockTtl());
        if (Boolean.TRUE.equals(locked)) {
            try {
                T loaded = loader.get();
                if (loaded == null && !options.cacheNullValues()) {
                    record(cacheName, "load_null_uncached");
                    return null;
                }
                put(cacheName, cacheKeyOnly(fullKey), loaded,
                        loaded == null ? options.withRedisTtl(options.nullTtl()) : options);
                record(cacheName, loaded == null ? "load_null" : "load");
                return loaded;
            } finally {
                redisTemplate.execute(unlockScript, List.of(lockKey), token);
            }
        }

        long deadline = System.nanoTime() + options.lockWait().toNanos();
        while (System.nanoTime() < deadline) {
            sleep(options.lockRetryInterval());
            String remote = redisTemplate.opsForValue().get(fullKey);
            if (remote != null) {
                boolean nullValue = NULL_VALUE.equals(remote);
                localCache.put(fullKey, LocalValue.of(remote, nullValue, options.localTtl()));
                record(cacheName, nullValue ? "wait_redis_null_hit" : "wait_redis_hit");
                return nullValue ? null : read(remote, typeReference);
            }
        }
        record(cacheName, "mutex_timeout");
        return loader.get();
    }

    private void recordHotKey(String cacheName, String fullKey) {
        long now = System.currentTimeMillis();
        boolean[] shouldReport = new boolean[1];
        HotKeyWindow window = hotKeyWindows.compute(fullKey, (ignored, current) -> {
            if (current == null || now - current.windowStartMillis() >= properties.hotKey().window().toMillis()) {
                return new HotKeyWindow(now, new AtomicLong(1), false);
            }
            current.counter().incrementAndGet();
            if (!current.reported() && current.counter().get() >= properties.hotKey().threshold()) {
                shouldReport[0] = true;
                return current.markReported();
            }
            return current;
        });
        if (shouldReport[0] && window != null) {
            Counter.builder("mall.cache.hot.key")
                    .description("Hot key threshold hits, tagged only by cache name to avoid high cardinality.")
                    .tag("cache", cacheName)
                    .register(meterRegistry)
                    .increment();
            log.warn("hot cache key detected: cache={} key={} requests={} windowMs={}",
                    cacheName, fullKey, window.counter().get(), properties.hotKey().window().toMillis());
        }
    }

    private void record(String cacheName, String result) {
        Counter.builder("mall.cache.multi.level.requests")
                .description("Multi-level cache request results.")
                .tag("cache", cacheName)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    private String fullKey(String cacheName, String key) {
        return properties.keyPrefix() + ":" + cacheName + ":" + key;
    }

    private String cacheKeyOnly(String fullKey) {
        String prefix = properties.keyPrefix() + ":";
        String withoutPrefix = fullKey.startsWith(prefix) ? fullKey.substring(prefix.length()) : fullKey;
        int split = withoutPrefix.indexOf(':');
        return split < 0 ? withoutPrefix : withoutPrefix.substring(split + 1);
    }

    private Duration ttlWithJitter(Duration ttl, double jitterRatio) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || jitterRatio <= 0) {
            return ttl;
        }
        long millis = ttl.toMillis();
        long jitter = Math.max(1, (long) (millis * jitterRatio));
        long delta = ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Duration.ofMillis(Math.max(1, millis + delta));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("cache value cannot be serialized", e);
        }
    }

    private <T> T read(String body, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(body, typeReference);
        } catch (Exception e) {
            throw new IllegalStateException("cache value cannot be deserialized", e);
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("cache mutex wait interrupted", e);
        }
    }

    private record LocalValue(String body, boolean nullValue, long expireAtMillis) {
        static LocalValue of(String body, boolean nullValue, Duration ttl) {
            return new LocalValue(body, nullValue, System.currentTimeMillis() + ttl.toMillis());
        }

        boolean expired() {
            return System.currentTimeMillis() >= expireAtMillis;
        }

    }

    private record HotKeyWindow(long windowStartMillis, AtomicLong counter, boolean reported) {
        HotKeyWindow markReported() {
            return new HotKeyWindow(windowStartMillis, counter, true);
        }
    }
}

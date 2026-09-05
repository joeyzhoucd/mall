package com.mall.common.cache;

import java.time.Duration;

public record MultiLevelCacheOptions(
        Duration localTtl,
        Duration redisTtl,
        Duration nullTtl,
        boolean cacheNullValues,
        Duration lockTtl,
        Duration lockWait,
        Duration lockRetryInterval,
        double jitterRatio
) {
    public static MultiLevelCacheOptions defaults() {
        return new MultiLevelCacheOptions(
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                true,
                Duration.ofSeconds(5),
                Duration.ofMillis(200),
                Duration.ofMillis(20),
                0.1
        );
    }

    public MultiLevelCacheOptions withRedisTtl(Duration ttl) {
        return new MultiLevelCacheOptions(localTtl, ttl, nullTtl, cacheNullValues, lockTtl, lockWait,
                lockRetryInterval, jitterRatio);
    }
}

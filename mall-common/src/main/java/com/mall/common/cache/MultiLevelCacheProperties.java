package com.mall.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "mall.cache.multi-level")
public record MultiLevelCacheProperties(
        boolean enabled,
        String keyPrefix,
        String invalidationChannel,
        Local local,
        HotKey hotKey
) {
    public MultiLevelCacheProperties {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            keyPrefix = "mall:cache";
        }
        if (invalidationChannel == null || invalidationChannel.isBlank()) {
            invalidationChannel = "mall:cache:multi-level:invalidate";
        }
        if (local == null) {
            local = new Local(10_000);
        }
        if (hotKey == null) {
            hotKey = new HotKey(Duration.ofSeconds(10), 1_000);
        }
    }

    public record Local(long maximumSize) {
        public Local {
            if (maximumSize <= 0) {
                maximumSize = 10_000;
            }
        }
    }

    public record HotKey(Duration window, long threshold) {
        public HotKey {
            if (window == null || window.isZero() || window.isNegative()) {
                window = Duration.ofSeconds(10);
            }
            if (threshold <= 0) {
                threshold = 1_000;
            }
        }
    }
}

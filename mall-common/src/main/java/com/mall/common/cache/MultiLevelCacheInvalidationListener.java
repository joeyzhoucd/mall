package com.mall.common.cache;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * 收到别的 pod 的 {@link MultiLevelCacheClient#evict} 广播后，丢掉本 pod 的本地副本。
 *
 * <p>单独成类（原来是配置类里的 lambda）是为了能测：多级缓存在 2026-09-05 ~ 09-28 因 key 解析 bug
 * 从未命中，这条失效链路也就从没在缓存真正生效时跑过。它一旦不工作，其他 pod 的本地副本
 * 要活满 localTtl —— 编辑商品后「有的请求新、有的请求旧」，而且不会报任何错。
 */
public class MultiLevelCacheInvalidationListener implements MessageListener {

    private final MultiLevelCacheClient cacheClient;

    public MultiLevelCacheInvalidationListener(MultiLevelCacheClient cacheClient) {
        this.cacheClient = cacheClient;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        cacheClient.invalidateLocal(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}

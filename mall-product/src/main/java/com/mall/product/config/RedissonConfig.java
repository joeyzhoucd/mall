package com.mall.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJackson3Codec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Arrays;

/**
 * Redisson 配置类。
 * <p>
 * 一主二从+哨兵上线后改成两种模式二选一，写法跟 mall-coupon 保持一致：配了
 * spring.data.redis.sentinel.nodes 就用哨兵模式，没配就退回单机模式
 * （Containers.java 的集成测试只写 host/port，靠这个分支自动落到单机模式）。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.sentinel.master:}")
    private String sentinelMaster;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private String sentinelNodes;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        if (StringUtils.hasText(sentinelNodes)) {
            String[] addresses = Arrays.stream(sentinelNodes.split(","))
                    .map(String::trim)
                    .map(node -> "redis://" + node)
                    .toArray(String[]::new);
            config.useSentinelServers()
                    .setMasterName(sentinelMaster)
                    .addSentinelAddress(addresses)
                    .setDatabase(0);
        } else {
            // 单节点模式
            config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort)
                    .setDatabase(0);
        }
        // 使用 JSON 序列化
        config.setCodec(new JsonJackson3Codec());

        return Redisson.create(config);
    }
}


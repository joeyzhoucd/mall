package com.mall.coupon.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJackson3Codec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类，写法跟 mall-product 里的保持一致。给对账任务
 * （SeckillReconciliationTask）的分布式锁用——Redisson 的锁默认带 watchdog
 * 自动续期，比手写 SETNX+固定TTL+Lua 安全释放更适合"执行时长不确定"的场景。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + redisHost + ":" + redisPort)
                .setDatabase(0);
        config.setCodec(new JsonJackson3Codec());
        return Redisson.create(config);
    }
}

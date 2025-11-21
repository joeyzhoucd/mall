package com.mall.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 配置类
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 单节点模式
        config.useSingleServer()
                .setAddress("redis://192.168.77.100:6379")
                .setDatabase(0);
        // 使用 JSON 序列化
        config.setCodec(new JsonJacksonCodec());
        
        return Redisson.create(config);
    }
}


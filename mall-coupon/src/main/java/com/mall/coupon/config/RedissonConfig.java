package com.mall.coupon.config;

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
 * Redisson 配置类，写法跟 mall-product 里的保持一致。给对账任务
 * （SeckillReconciliationTask）的分布式锁用——Redisson 的锁默认带 watchdog
 * 自动续期，比手写 SETNX+固定TTL+Lua 安全释放更适合"执行时长不确定"的场景。
 * <p>
 * 一主二从+哨兵上线后改成两种模式二选一：配了 spring.data.redis.sentinel.nodes
 * 就用哨兵模式（生产），没配就退回单机模式（Containers.java 那套集成测试用
 * Testcontainers 起一个单独的 Redis 容器，只会写 host/port，不会写 sentinel.nodes，
 * 靠这个分支自动落到单机模式，测试代码不用跟着改）。
 * 不是 Spring 自己的 RedisConnectionFactory——Redisson 是独立的客户端，两边的
 * 连接方式要各配一遍，这也是为什么这个类没法跟着 Spring 的 sentinel 属性自动生效。
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
            config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort)
                    .setDatabase(0);
        }
        config.setCodec(new JsonJackson3Codec());
        return Redisson.create(config);
    }
}

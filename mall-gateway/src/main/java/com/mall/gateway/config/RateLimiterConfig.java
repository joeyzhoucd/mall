package com.mall.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * 给 Spring Cloud Gateway 内置的 RequestRateLimiter 过滤器提供"按什么分桶限流"的策略。
 * <p>
 * 秒杀这种场景，真正要防的是"这个路由收到的总请求量把下游（mall-coupon、Redis、
 * 数据库）冲垮"，不是"某一个人/某一个IP 刷得比别人凶"——后者按 IP 或按用户限流才
 * 有意义，但秒杀本来就是海量不同用户在同一时刻各自发一两个请求，按 IP/按用户限流
 * 根本挡不住这种"人多但每个人请求量都不大"的合法流量。所以这里用一个全局 key
 * （所有请求都算进同一个令牌桶），直接限制这条路由整体每秒能放行多少请求，不管
 * 这些请求来自多少个不同的人。
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver seckillGlobalKeyResolver() {
        return exchange -> Mono.just("seckill-global");
    }
}

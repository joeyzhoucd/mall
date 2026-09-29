package com.mall.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * outbox「提交后立即发一次」用的执行器。
 *
 * <h3>为什么要离开提交线程（2026-09-29）</h3>
 * Spring 的 afterCommit / afterCompletion 回调都跑在 cleanupAfterCompletion 释放连接<b>之前</b>：
 * 事务已经提交，连接还绑在线程上。原来在回调里同步 publish（getById → 认领 update → getById →
 * rabbit send），下单一单的连接因此多占 70~180ms。Tempo 对比：submitOrder 去掉大事务后连接占用
 * p50 403 → 234ms，SQL 只有 48ms，剩下的主要就是这段。交给这里之后连接随提交立即归还，
 * 发送时的几条 SQL 各自短暂借用连接。
 *
 * <h3>为什么是固定 2 个线程，而不是 applicationTaskExecutor</h3>
 * 开了虚拟线程后后者并发不设上限：高峰时同时冒出几百个发送任务，一起去抢每 pod 5 条的连接池，
 * 反而把请求线程挤出去。2 个线程 + 1000 的队列足够跟上下单速率；队列满了就放弃这一次立即发送
 * （OrderOutboxMessageServiceImpl 里记日志）—— 消息已经落在 outbox 表里，relay 每 5s 一轮会补发，
 * 立即发送本来就只是加速，不是可靠性来源。
 */
@Configuration
public class OrderOutboxPublishExecutorConfig {

    public static final String BEAN = "orderOutboxPublishExecutor";

    @Bean(BEAN)
    public ThreadPoolTaskExecutor orderOutboxPublishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("outbox-publish-");
        // 下线时把队列里的发完再走（最多 5s），剩下的交给下一个实例的 relay
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}

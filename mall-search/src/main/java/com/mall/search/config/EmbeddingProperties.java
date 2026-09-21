package com.mall.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 向量化服务（TEI）的接入参数。
 *
 * <h3>这个依赖和别的外部依赖不一样</h3>
 * 它在<b>每一次搜索的关键路径上</b>：用户输入的查询词必须先变成向量，才能做语义检索。
 * 而它又是<b>可降级</b>的 —— 拿不到向量就退回纯关键词搜索，用户看到的是
 * 「结果差一些」，不是「搜索崩了」。
 * <p>
 * 这两条合起来决定了下面每个默认值的取法：超时要短（不能让它拖慢搜索），
 * 熔断要快（挂了就别再试），恢复也要快（降级代价小，值得频繁试探）。
 */
@ConfigurationProperties(prefix = "mall.search.embedding")
public record EmbeddingProperties(
        Boolean enabled,
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Integer slidingWindowSize,
        Integer minimumNumberOfCalls,
        Float failureRateThreshold,
        Duration waitDurationInOpenState,
        Integer permittedNumberOfCallsInHalfOpenState
) {

    /** 熔断器实例名。指标里会以 {@code name="search-embedding"} 的标签出现。 */
    public static final String CIRCUIT_BREAKER_NAME = "search-embedding";

    public EmbeddingProperties {
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            // K8s 的 Service DNS。和 Config Server 一样直接用 Service 名：
            // 这不是业务服务，没必要进服务发现再多一层依赖。
            baseUrl = "http://embedding";
        }
        // 【连接超时 200ms】同一个集群内建 TCP 连接是毫秒级的事。
        // 设这么短是为了区分「TEI 慢」和「TEI 根本不在」——后者应该立刻失败，
        // 而不是占满整个读超时预算。
        if (connectTimeout == null) {
            connectTimeout = Duration.ofMillis(200);
        }
        // 【读超时 500ms】实测 TEI 单条文本 p50 ≈ 10ms、p95 ≈ 28ms（经 port-forward，
        // 含转发开销，集群内只会更快）。500ms 是 p95 的约 18 倍，留足 GC、
        // CPU 抢占、冷启动的余量，同时保证【最坏情况下搜索也只慢半秒】。
        //
        // 不要把它调大。调大的唯一效果是 TEI 卡死时，每个搜索请求都白等更久 ——
        // 而等到的仍然是失败。真正防止「每次都白等」的是下面的熔断，不是超时。
        if (readTimeout == null) {
            readTimeout = Duration.ofMillis(500);
        }
        if (slidingWindowSize == null) {
            slidingWindowSize = 20;
        }
        // 少于 10 次调用不判断，避免服务刚起来时一两次失败就把电路打开
        if (minimumNumberOfCalls == null) {
            minimumNumberOfCalls = 10;
        }
        if (failureRateThreshold == null) {
            failureRateThreshold = 50f;
        }
        // 【5 秒，比 Feign 默认的 10 秒短一半】
        // 这是按【降级代价】定的，不是抄来的。Feign 那些调用降级意味着下单失败、
        // 库存对不上；这里降级只是搜索结果变差，用户多半察觉不到。
        // 代价越小，越该频繁试探恢复 —— 早一秒恢复语义搜索，就早一秒把质量找回来。
        if (waitDurationInOpenState == null) {
            waitDurationInOpenState = Duration.ofSeconds(5);
        }
        if (permittedNumberOfCallsInHalfOpenState == null) {
            permittedNumberOfCallsInHalfOpenState = 3;
        }
    }
}

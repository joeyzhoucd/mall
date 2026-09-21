package com.mall.search.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    /**
     * 专用的 RestClient，<b>必须带超时</b>。
     *
     * <p>注意不要复用注入进来的共享 {@code RestClient.Builder} 的默认设置就完事 ——
     * Spring 默认不设读超时，那意味着 TEI 卡住时搜索线程会<b>一直等下去</b>。
     * 本项目的 {@code PaymentGatewayClient} 就是这么写的（那是支付回调，不在
     * 用户等待的路径上，影响小），这里不能照抄。
     */
    @Bean
    public RestClient embeddingRestClient(EmbeddingProperties properties, RestClient.Builder builder) {
        // Boot 4 把 3.x 的 ClientHttpRequestFactorySettings 改名成了 HttpClientSettings，
        // 包没变（org.springframework.boot.http.client）。照 3.x 的写法抄会编译不过。
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    /**
     * 向量化调用的熔断器。
     *
     * <h3>为什么光有超时不够，必须再加熔断</h3>
     * 超时保证的是「单次调用最多等 500ms」。但 TEI 真挂掉时，<b>每一个</b>搜索请求
     * 都会先等满 500ms 再降级 —— 搜索整体延迟从 ~10ms 变成 ~510ms，
     * 用户感受上和故障没区别，而这 500ms 完全是白等，反正结果都是失败。
     * <p>
     * 熔断的作用就是把「白等」去掉：连续失败到阈值后电路打开，后续调用<b>立即</b>
     * 返回不可用，搜索直接走纯关键词，延迟回到 ~10ms。
     *
     * <h3>为什么不用 default 配置</h3>
     * {@code resilience4j.circuitbreaker.configs.default} 是给 Feign 调用定的，
     * 而那些调用的降级代价（下单失败、库存对不上）比这里大得多。
     * 参数该按调用的<b>降级代价</b>来定，不是按「别处怎么配的」来定 ——
     * 具体每个值的理由见 {@link EmbeddingProperties}。
     *
     * <h3>为什么走 registry 而不是自己 new</h3>
     * {@code registry.circuitBreaker(name, config)} 会把实例登记进注册表，
     * resilience4j 的 {@code TaggedCircuitBreakerMetrics} 监听注册表的新增事件，
     * 于是这个熔断器的状态<b>自动出现在 /actuator/prometheus 里</b>
     * （{@code resilience4j_circuitbreaker_state{name="search-embedding"}}）。
     * 自己 new 一个就没有这些指标，而降级是静默的，没指标等于没人知道它发生过。
     */
    @Bean
    public CircuitBreaker embeddingCircuitBreaker(CircuitBreakerRegistry registry,
                                                  EmbeddingProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedNumberOfCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .build();
        return registry.circuitBreaker(EmbeddingProperties.CIRCUIT_BREAKER_NAME, config);
    }
}

package com.mall.search.client;

import com.mall.search.config.EmbeddingProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 把查询文本变成向量。调用的是集群内的 TEI（text-embeddings-inference）服务。
 *
 * <h3>这个类的核心约定：失败不抛异常，返回 {@code null}</h3>
 * 拿不到向量在这里<b>不是错误，是一种正常结果</b> —— 它的含义是
 * 「这次搜索没有语义能力，用关键词搜」。调用方据此选择检索方式，
 * 而不该被迫为一个「可降级」的依赖写 try/catch。
 * <p>
 * 这和 {@code mall-order} 里
 * <a href="#">loadUsableCoupons</a> 的取舍是同一类：
 * 券查不到就按无券展示，不该让结算页打不开。反过来，
 * 「预览抵扣金额」那种调用就<b>不可</b>降级，降级成 0 等于按原价扣款。
 * <b>降级策略必须按调用分类，不能按服务分类。</b>
 *
 * <h3>为什么这里的降级特别容易被忽略</h3>
 * 因为它<b>完全静默</b>：TEI 挂了，搜索页照常打开、照常出结果，只是
 * 「电饭锅」又搜不到「厨房电器」了。没有报错、没有 5xx、没有白屏。
 * 所以每一条路径都打了指标（见下面的 {@code result} 标签），
 * 让「搜索在降级运行」这件事在监控上是可见的。
 */
@Slf4j
@Component
public class EmbeddingClient {

    /** 调用结果计数。result 取值：ok / fail / short_circuit / disabled / empty */
    private static final String METRIC_CALLS = "mall.search.embedding.calls";
    /** 调用耗时。只记真正发出去的请求，熔断拒绝和禁用不计入。 */
    private static final String METRIC_DURATION = "mall.search.embedding.duration";

    private final RestClient embeddingRestClient;
    private final CircuitBreaker circuitBreaker;
    private final EmbeddingProperties properties;
    private final MeterRegistry meterRegistry;

    public EmbeddingClient(RestClient embeddingRestClient,
                           CircuitBreaker embeddingCircuitBreaker,
                           EmbeddingProperties properties,
                           MeterRegistry meterRegistry) {
        this.embeddingRestClient = embeddingRestClient;
        this.circuitBreaker = embeddingCircuitBreaker;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 把一段文本变成向量。
     *
     * @param text 查询词
     * @return 512 维向量；<b>{@code null} 表示本次拿不到向量，调用方应退回纯关键词检索</b>
     */
    public float[] embed(String text) {
        if (!properties.enabled()) {
            count("disabled");
            return null;
        }
        if (text == null || text.isBlank()) {
            // 不是故障，是没东西可向量化。单独计数，免得和真失败混在一起
            // 让「失败率」这个指标失真。
            count("empty");
            return null;
        }

        try {
            return circuitBreaker.executeSupplier(() -> callTei(text));
        } catch (CallNotPermittedException e) {
            // 电路是开的，请求根本没发出去。这条路径是【正常工作】的表现，
            // 不是异常 —— 它恰恰说明熔断在按设计保护搜索延迟，所以只用 debug。
            count("short_circuit");
            log.debug("向量化熔断中，本次搜索降级为纯关键词 text={}", text);
            return null;
        } catch (Exception e) {
            count("fail");
            // warn 而不是 error：搜索仍然可用，只是质量下降。
            // 但必须留痕 —— 否则「语义搜索怎么不灵了」查无可查。
            log.warn("向量化失败，本次搜索降级为纯关键词 text={}", text, e);
            return null;
        }
    }

    /**
     * 真正发请求。<b>这个方法里的异常必须往外抛</b>，否则熔断器统计不到失败，
     * 电路永远不会打开 —— 那样超时就白设了，每次都要等满 500ms。
     */
    private float[] callTei(String text) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // TEI 的 /embed 接口收一个数组、返回一个数组（支持批量），
            // 所以单条查询也要包成一个元素，再从结果里取第 0 个。
            float[][] response = embeddingRestClient.post()
                    .uri("/embed")
                    .body(Map.of("inputs", new String[]{text}))
                    .retrieve()
                    .body(float[][].class);

            if (response == null || response.length == 0 || response[0] == null || response[0].length == 0) {
                // HTTP 200 但内容是空的。这必须算失败并计入熔断统计，
                // 否则 TEI 处于「能应答但答不出东西」的状态时电路永远不开。
                throw new IllegalStateException("向量化服务返回空结果");
            }
            sample.stop(timer("ok"));
            count("ok");
            return response[0];
        } catch (RuntimeException e) {
            sample.stop(timer("fail"));
            throw e;
        }
    }

    private void count(String result) {
        Counter.builder(METRIC_CALLS)
                .tag("result", result)
                .description("向量化调用结果分布；ok 以外的都意味着这次搜索在降级运行")
                .register(meterRegistry)
                .increment();
    }

    private Timer timer(String result) {
        return Timer.builder(METRIC_DURATION)
                .tag("result", result)
                .description("向量化调用耗时；它直接加在每次搜索的延迟上")
                .register(meterRegistry);
    }
}

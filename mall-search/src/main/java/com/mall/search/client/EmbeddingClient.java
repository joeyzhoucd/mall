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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    /**
     * 一次请求最多喂多少条文本。TEI 的 {@code max_client_batch_size} 就是 32
     * （实测 /info 里可查），超过会被服务端拒绝，所以批量调用必须自己切片。
     */
    private static final int MAX_BATCH = 32;

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
            float[] vector = circuitBreaker.executeSupplier(() -> callTei(List.of(text))[0]);
            count("ok");
            return vector;
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
     * 批量向量化，<b>失败会抛异常</b>——和 {@link #embed} 的降级语义正好相反。
     *
     * <h3>为什么同一个依赖，这里不能降级</h3>
     * 搜索时拿不到向量，代价是「这一次搜索质量差」，下次就好了。
     * 而<b>上架时</b>跳过向量，代价是「这个商品从此再也不会出现在语义搜索里」——
     * 除非有人想起来重新上架一次。这是永久性的静默数据缺失，
     * 比「上架失败让调用方重试」糟糕得多。
     * <p>
     * 这和 {@code mall-order} 里
     * 「可降级的 loadUsableCoupons」与「不可降级的预览抵扣金额」是同一组对照：
     * <b>同一个下游，不同的调用，降级策略可以完全相反。</b>
     *
     * @param texts 待向量化的文本
     * @return 与入参一一对应的向量；<b>{@code null} 表示功能被整体关闭</b>
     *         （{@code enabled=false}），调用方应当不写向量字段而继续
     * @throws RuntimeException TEI 不可用或应答异常，调用方<b>不应</b>吞掉
     */
    public List<float[]> embedAll(List<String> texts) {
        if (!properties.enabled()) {
            // 整个语义搜索被关掉了，搜索侧也不会用向量，这里跟着不生成是一致的。
            // 代价：关闭期间上架的商品没有向量，重新开启后需要补灌一次。
            count("disabled");
            return null;
        }
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += MAX_BATCH) {
            List<String> chunk = texts.subList(i, Math.min(i + MAX_BATCH, texts.size()));
            try {
                float[][] vectors = circuitBreaker.executeSupplier(() -> callTei(chunk));
                result.addAll(Arrays.asList(vectors));
            } catch (RuntimeException e) {
                count("batch_fail");
                // error 而不是 warn：这一条会让上架失败，是需要人处理的事件。
                log.error("批量向量化失败，共 {} 条，本次上架将失败", texts.size(), e);
                throw e;
            }
        }
        count("batch_ok");
        return result;
    }

    /**
     * 真正发请求。<b>这个方法里的异常必须往外抛</b>，否则熔断器统计不到失败，
     * 电路永远不会打开 —— 那样超时就白设了，每次都要等满 500ms。
     */
    private float[][] callTei(List<String> texts) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            float[][] response = embeddingRestClient.post()
                    .uri("/embed")
                    .body(Map.of("inputs", texts))
                    .retrieve()
                    .body(float[][].class);

            if (response == null || response.length != texts.size()) {
                // HTTP 200 但条数对不上。必须算失败并计入熔断统计，否则 TEI 处于
                // 「能应答但答不对」的状态时电路永远不开。
                // 条数对不上尤其危险：批量场景下会让向量和商品【错位】，
                // 那是比没有向量更坏的结果。
                throw new IllegalStateException("向量化服务返回条数不符，期望 "
                        + texts.size() + " 实际 " + (response == null ? "null" : response.length));
            }
            for (float[] v : response) {
                if (v == null || v.length == 0) {
                    throw new IllegalStateException("向量化服务返回了空向量");
                }
            }
            sample.stop(timer("ok"));
            return response;
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

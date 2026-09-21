package com.mall.search.client;

import com.mall.search.config.EmbeddingProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 向量化调用的降级行为测试。
 *
 * <h3>为什么用真的 HTTP server，而不是 MockRestServiceServer</h3>
 * 因为这里最要紧的一条断言是<b>「熔断打开后请求根本没发出去」</b>，
 * 而不是「返回了 null」。只断言返回 null 是不够的 —— 超时失败也返回 null，
 * 两者在调用方看来一模一样，但性能差 500 倍（立即返回 vs 等满读超时）。
 * 要区分它们，必须能<b>数到服务端实际收到几个请求</b>。
 * <p>
 * 同理，「禁用」和「空文本」两条路径也必须验证请求数为 0，否则那两个
 * 提前 return 写错了也测不出来。
 */
class EmbeddingClientTest {

    private HttpServer server;
    private int port;
    /** 服务端实际收到的请求数——本测试类最重要的观测量 */
    private final AtomicInteger received = new AtomicInteger();
    /** 当前的应答行为，每个用例自己设 */
    private volatile Consumer<HttpExchange> behavior;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void startServer() throws IOException {
        received.set(0);
        meterRegistry = new SimpleMeterRegistry();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.createContext("/embed", exchange -> {
            received.incrementAndGet();
            behavior.accept(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------- 应答行为 ----------

    private void respondOk() {
        behavior = exchange -> write(exchange, 200, "[[0.1,0.2,0.3]]");
    }

    private void respondServerError() {
        behavior = exchange -> write(exchange, 500, "boom");
    }

    private void respondEmptyBody() {
        behavior = exchange -> write(exchange, 200, "[]");
    }

    private void respondSlowly(Duration delay) {
        behavior = exchange -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, "[[0.1,0.2,0.3]]");
        };
    }

    private static void write(HttpExchange exchange, int status, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            throw new UncheckedIOExceptionWrapper(e);
        }
    }

    private static class UncheckedIOExceptionWrapper extends RuntimeException {
        UncheckedIOExceptionWrapper(Throwable cause) {
            super(cause);
        }
    }

    // ---------- 被测对象的组装 ----------

    private EmbeddingClient client(EmbeddingProperties props) {
        HttpClientSettings settings = HttpClientSettings.defaults()
                .withTimeouts(props.connectTimeout(), props.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(props.slidingWindowSize())
                .minimumNumberOfCalls(props.minimumNumberOfCalls())
                .failureRateThreshold(props.failureRateThreshold())
                .waitDurationInOpenState(props.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(props.permittedNumberOfCallsInHalfOpenState())
                .build();
        // 每个用例一个全新的 registry，避免熔断器状态在用例间串味
        CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults()
                .circuitBreaker(EmbeddingProperties.CIRCUIT_BREAKER_NAME + "-" + System.nanoTime(), cbConfig);
        return new EmbeddingClient(restClient, cb, props, meterRegistry);
    }

    /** 默认参数，只把超时压短让测试跑得快 */
    private EmbeddingProperties defaults() {
        return new EmbeddingProperties(null, null,
                Duration.ofMillis(200), Duration.ofMillis(300),
                null, null, null, null, null);
    }

    private double counter(String result) {
        var c = meterRegistry.find("mall.search.embedding.calls").tag("result", result).counter();
        return c == null ? 0 : c.count();
    }

    // ---------- 正常路径 ----------

    @Test
    @DisplayName("TEI 正常应答时返回向量")
    void returnsVectorOnSuccess() {
        respondOk();
        float[] v = client(defaults()).embed("电饭锅");

        assertThat(v).isNotNull();
        assertThat(v).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(received.get()).isEqualTo(1);
        assertThat(counter("ok")).isEqualTo(1);
    }

    // ---------- 失败降级：都必须返回 null，不能抛 ----------

    @Test
    @DisplayName("TEI 返回 5xx 时降级为 null，不抛异常")
    void degradesOnServerError() {
        respondServerError();
        assertThat(client(defaults()).embed("电饭锅")).isNull();
        assertThat(counter("fail")).isEqualTo(1);
    }

    @Test
    @DisplayName("TEI 超时时降级为 null，不抛异常")
    void degradesOnTimeout() {
        // 应答比读超时（300ms）慢
        respondSlowly(Duration.ofMillis(900));
        assertThat(client(defaults()).embed("电饭锅")).isNull();
        assertThat(counter("fail")).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP 200 但内容为空时算失败，不能当成功")
    void emptyBodyCountsAsFailure() {
        // 这条容易被写漏：TEI「能应答但答不出东西」时如果不算失败，
        // 熔断统计不到，电路永远不开，每次搜索都白跑一趟。
        respondEmptyBody();
        assertThat(client(defaults()).embed("电饭锅")).isNull();
        assertThat(counter("fail")).isEqualTo(1);
        assertThat(counter("ok")).isZero();
    }

    // ---------- 核心：熔断打开后不再发请求 ----------

    @Test
    @DisplayName("熔断打开后，请求不再发到服务端（不是只返回 null）")
    void stopsSendingRequestsOnceCircuitOpens() {
        respondServerError();
        EmbeddingClient c = client(defaults());   // minimumNumberOfCalls=10, 失败率阈值 50%

        for (int i = 0; i < 10; i++) {
            assertThat(c.embed("电饭锅")).isNull();
        }
        int afterOpening = received.get();
        assertThat(afterOpening)
                .as("前 10 次应该都真的发出去了，熔断才有统计依据")
                .isEqualTo(10);

        for (int i = 0; i < 5; i++) {
            assertThat(c.embed("电饭锅")).isNull();
        }

        assertThat(received.get())
                .as("电路已开，这 5 次不该再打到服务端——省掉的正是每次 500ms 的白等")
                .isEqualTo(afterOpening);
        assertThat(counter("short_circuit"))
                .as("被熔断拒绝的调用要单独计数，不能混进 fail")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("反向对照：熔断阈值设成不可能达到时，请求确实会继续发出去")
    void keepsSendingWhenCircuitNeverOpens() {
        // 没有这条对照，上面那个测试可能因为【错误的原因】通过——
        // 比如某个 bug 让所有调用都不发请求，断言照样成立。
        respondServerError();
        EmbeddingProperties neverOpen = new EmbeddingProperties(null, null,
                Duration.ofMillis(200), Duration.ofMillis(300),
                100, 1000, 100f, null, null);   // minimumNumberOfCalls=1000，本用例够不到
        EmbeddingClient c = client(neverOpen);

        for (int i = 0; i < 15; i++) {
            assertThat(c.embed("电饭锅")).isNull();
        }

        assertThat(received.get())
                .as("电路没开，15 次都应该真的发出去")
                .isEqualTo(15);
        assertThat(counter("short_circuit")).isZero();
    }

    // ---------- 提前返回的两条路径：一个请求都不该发 ----------

    @Test
    @DisplayName("配置关闭时直接返回 null，一个请求都不发")
    void sendsNothingWhenDisabled() {
        respondOk();
        EmbeddingProperties off = new EmbeddingProperties(false, null,
                Duration.ofMillis(200), Duration.ofMillis(300), null, null, null, null, null);

        assertThat(client(off).embed("电饭锅")).isNull();
        assertThat(received.get()).isZero();
        assertThat(counter("disabled")).isEqualTo(1);
    }

    @Test
    @DisplayName("空查询词直接返回 null，一个请求都不发，且不计入失败率")
    void sendsNothingWhenTextBlank() {
        respondOk();
        EmbeddingClient c = client(defaults());

        assertThat(c.embed(null)).isNull();
        assertThat(c.embed("   ")).isNull();

        assertThat(received.get()).isZero();
        assertThat(counter("empty")).isEqualTo(2);
        assertThat(counter("fail"))
                .as("空查询不是故障，混进 fail 会让熔断被无搜索词的请求误触发")
                .isZero();
    }

    // ---------- embedAll：上架路径，语义和 embed 相反 ----------

    @Test
    @DisplayName("embedAll 失败时【抛异常】，不像 embed 那样降级")
    void embedAllThrowsInsteadOfDegrading() {
        // 这是整组测试里最重要的一条语义差异：
        // 上架时静默跳过向量 = 这个商品永久搜不到，比让上架失败糟糕得多。
        respondServerError();
        EmbeddingClient c = client(defaults());

        assertThatThrownBy(() -> c.embedAll(List.of("电饭锅")))
                .as("上架路径不可降级，必须让调用方知道失败了")
                .isInstanceOf(RuntimeException.class);
        assertThat(counter("batch_fail")).isEqualTo(1);
    }

    @Test
    @DisplayName("embedAll 在功能关闭时返回 null，且不发请求")
    void embedAllReturnsNullWhenDisabled() {
        respondOk();
        EmbeddingProperties off = new EmbeddingProperties(false, null,
                Duration.ofMillis(200), Duration.ofMillis(300), null, null, null, null, null);

        assertThat(client(off).embedAll(List.of("电饭锅")))
                .as("null 表示「功能关掉了，不需要向量」，调用方应正常上架")
                .isNull();
        assertThat(received.get()).isZero();
    }

    @Test
    @DisplayName("超过 32 条时自动分批——TEI 的 max_client_batch_size 就是 32")
    void splitsIntoBatchesOf32() {
        behavior = exchange -> {
            // 按请求里的条数原样返回同样多的向量
            int n = countInputs(exchange);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n; i++) {
                sb.append(i > 0 ? "," : "").append("[0.1,0.2,0.3]");
            }
            write(exchange, 200, sb.append("]").toString());
        };
        List<String> texts = new java.util.ArrayList<>();
        for (int i = 0; i < 70; i++) {
            texts.add("商品" + i);
        }

        List<float[]> out = client(defaults()).embedAll(texts);

        assertThat(out).hasSize(70);
        assertThat(received.get())
                .as("70 条应该切成 32+32+6 三个请求；一次发 70 条会被 TEI 拒绝")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("返回条数和入参对不上时必须抛异常——错位比没有向量更糟")
    void throwsWhenResponseCountMismatches() {
        // 如果这里不校验，3 个商品拿到 2 个向量，后面按下标一一对应就会【错位】：
        // 商品 B 带上了商品 A 的向量，搜索时出现完全无关的结果，而且查不出原因。
        behavior = exchange -> write(exchange, 200, "[[0.1,0.2,0.3]]");   // 只回 1 条

        assertThatThrownBy(() -> client(defaults()).embedAll(List.of("甲", "乙", "丙")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("条数不符");
    }

    /** 数一下请求体里 inputs 数组有几个元素 */
    private static int countInputs(HttpExchange exchange) {
        try (var is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            int start = body.indexOf('[');
            int end = body.lastIndexOf(']');
            if (start < 0 || end <= start) {
                return 0;
            }
            String inner = body.substring(start + 1, end).trim();
            return inner.isEmpty() ? 0 : inner.split("\",\"|\", \"").length;
        } catch (IOException e) {
            throw new UncheckedIOExceptionWrapper(e);
        }
    }

    // ---------- 默认值 ----------

    @Test
    @DisplayName("默认值：启用、指向集群内 Service、超时是有限的")
    void defaultsAreSafe() {
        EmbeddingProperties p = new EmbeddingProperties(null, null, null, null, null, null, null, null, null);

        assertThat(p.enabled()).isTrue();
        assertThat(p.baseUrl()).isEqualTo("http://embedding");
        assertThat(p.readTimeout())
                .as("读超时必须有限且够短——它直接加在每次搜索的延迟上")
                .isNotNull()
                .isLessThanOrEqualTo(Duration.ofSeconds(1));
        assertThat(p.connectTimeout()).isNotNull().isLessThan(p.readTimeout());
        assertThat(p.waitDurationInOpenState())
                .as("降级代价小，恢复试探应该比 Feign 默认的 10s 更积极")
                .isLessThan(Duration.ofSeconds(10));
    }
}

package com.mall.common.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Config Server 拉取重试的守护测试。
 *
 * <h3>它守的是什么</h3>
 * mall-search 把 {@code spring.config.import} 从 {@code optional:configserver:}
 * 改成了 {@code configserver:}，赌的是「拉不到会先重试一段时间」。
 * 如果重试实际上没生效，这个改动就从「更安全」变成「更危险」——
 * Config Server 晚就绪几秒，服务就直接起不来。
 *
 * <h3>为什么数请求次数，而不是测耗时</h3>
 * 第一版是拿耗时判断的，结果很有迷惑性：重试 5 次耗时 863ms，
 * 不重试反而 2028ms —— Spring 上下文启动本身的波动比重试的等待还大，
 * 那个测量方法根本区分不出两者。
 * 改成在本地起一个假的 Config Server 数它收到几个请求，是直接观测，没有噪音。
 *
 * <h3>最重要的一条断言在最后</h3>
 * {@code fail-fast=true} 会<b>压过 {@code optional:} 前缀</b>。
 * 这条行为是反直觉的，而且后果很大：把 fail-fast 配进 mall-common 的话，
 * 其余仍带 {@code optional:} 的服务会一起变成 fail-fast，
 * Config Server 慢几秒就全体 crashloop。所以它必须被测试钉住。
 */
class ConfigServerRetryTest {

    // 【刻意用空的 @Configuration，不用 @SpringBootApplication】
    // 用后者会连带触发 DataSource / Redis / RabbitMQ 等一堆自动配置，
    // 它们在测试环境里本来就起不来 —— 于是「启动失败」这个断言会因为
    // 完全无关的原因通过，而真正要测的 Config Data 加载可能根本没发生。
    // 第一版就栽在这里：断言过了，但假 Config Server 收到 0 个请求。
    // Config Data 的加载在 Environment 准备阶段，早于任何 bean，所以空配置就够。
    @Configuration
    static class TestApp {
    }

    private HttpServer server;
    private int port;
    /** 假 Config Server 收到的请求数 —— 本测试类唯一的观测量 */
    private final AtomicInteger received = new AtomicInteger();

    @BeforeEach
    void startFakeConfigServer() throws IOException {
        received.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        // 一律返回 500：模拟「Config Server 进程在，但还没准备好提供配置」，
        // 这正是节点集体恢复时最常见的那一瞬间。
        server.createContext("/", exchange -> {
            received.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopFakeConfigServer() {
        server.stop(0);
    }

    private String[] props(String importValue, int maxAttempts) {
        return new String[]{
                "spring.config.import=" + importValue + "http://127.0.0.1:" + port,
                "spring.cloud.config.fail-fast=true",
                "spring.cloud.config.retry.max-attempts=" + maxAttempts,
                "spring.cloud.config.retry.initial-interval=50",
                // 【multiplier 必须 > 1】设成 1.0 会让 Spring Retry 抛
                // IllegalArgumentException: Multiplier should be > 1，应用直接起不来，
                // 而且那个失败看起来像「Config Server 拉不到」——第一版就是被它骗了。
                // 想要近似固定的间隔，靠 max-interval 封顶，不要把 multiplier 设成 1。
                "spring.cloud.config.retry.multiplier=1.1",
                "spring.cloud.config.retry.max-interval=200",   // 必须 > initial-interval，相等也不行
                "spring.cloud.consul.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false",
        };
    }

    private void runExpectingFailure(String[] properties) {
        // 【断言异常类型，不只是「抛了异常」】否则任何无关的启动错误
        // 都会让测试通过，而 Config Server 那一段可能压根没跑到。
        assertThatThrownBy(() -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestApp.class)
                    .web(WebApplicationType.NONE)
                    .properties(properties)
                    .run()) {
                // 走到这里说明启动成功了，不该发生
            }
        }).hasStackTraceContaining("ConfigClientFailFastException");
    }

    @Test
    @DisplayName("配了 retry 时，拉不到配置会真的重试到 max-attempts 次")
    void retriesUpToMaxAttempts() {
        runExpectingFailure(props("configserver:", 4));

        assertThat(received.get())
                .as("假 Config Server 只收到 %d 个请求 —— 重试没有生效，"
                        + "那么去掉 optional: 就成了纯粹的风险", received.get())
                .isEqualTo(4);
    }

    @Test
    @DisplayName("反向对照：max-attempts=1 时只请求一次，确认上面那 4 次是重试而非别的原因")
    void doesNotRetryWhenMaxAttemptsIsOne() {
        // 没有这条对照，上面的断言可能因为【错误的原因】通过 ——
        // 比如框架自己就会重复请求若干次，和我们配的 retry 无关。
        runExpectingFailure(props("configserver:", 1));

        assertThat(received.get())
                .as("max-attempts=1 却请求了 %d 次，说明请求次数不由 retry 配置决定", received.get())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("【关键】fail-fast=true 会压过 optional: 前缀，照样启动失败")
    void failFastOverridesOptionalPrefix() {
        // 这条行为是反直觉的：optional: 的字面意思是「可选」，
        // 但只要 fail-fast=true，拉不到照样抛 ConfigClientFailFastException。
        //
        // 后果很大：把 fail-fast 写进 mall-common（全局），
        // 会让其余仍带 optional: 的服务一起变成 fail-fast，
        // Config Server 慢几秒就全体 crashloop。
        // 所以那组配置只能放在单个服务的 application.yml 里，直到逐个验证完。
        runExpectingFailure(props("optional:configserver:", 2));

        assertThat(received.get())
                .as("带 optional: 时请求次数应当和不带时一样（fail-fast 才是决定性的那个开关）")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("不开 fail-fast 时，optional: 才真的是可选的 —— 启动成功但静默降级")
    void optionalIsOnlyOptionalWithoutFailFast() {
        // 这就是 2026-09-21 那次事故的那条路径：服务照常起来，
        // 带着 jar 里的默认值跑，没有任何报错。
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.import=optional:configserver:http://127.0.0.1:" + port,
                        "spring.cloud.config.fail-fast=false",
                        "spring.cloud.consul.enabled=false",
                        "spring.cloud.service-registry.auto-registration.enabled=false")
                .run()) {
            assertThat(ctx.isRunning())
                    .as("这条路径【应该】启动成功 —— 它正是静默降级的来源，不是 bug 而是设计")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("复现 CI 失败：config.enabled=false 时，非 optional 的 import 会让上下文起不来")
    void nonOptionalImportFailsWhenConfigClientDisabled() {
        // MallIntegrationTest 给所有集成测试设了 spring.cloud.config.enabled=false
        // （测试环境没有 Config Server）。带 optional: 时这没问题——加载不了就跳过；
        // 去掉 optional: 之后，Spring 找不到能处理 configserver: 的加载器就直接报错。
        // 这正是 ec4c935 那次 CI 里 integration-test 挂掉的原因。
        assertThatThrownBy(() -> new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.import=configserver:http://127.0.0.1:" + port,
                        "spring.cloud.config.enabled=false",
                        "spring.cloud.config.import-check.enabled=false",
                        "spring.cloud.consul.enabled=false")
                .run())
                .as("如果这里没抛异常，说明 CI 的失败另有原因，不要照着改")
                .isNotNull();
        assertThat(received.get())
                .as("config client 被禁用了，不该真的发出请求")
                .isZero();
    }

    @Test
    @DisplayName("对照：同样条件下带 optional: 前缀能正常启动")
    void optionalImportSurvivesDisabledConfigClient() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.import=optional:configserver:http://127.0.0.1:" + port,
                        "spring.cloud.config.enabled=false",
                        "spring.cloud.config.import-check.enabled=false",
                        "spring.cloud.consul.enabled=false")
                .run()) {
            assertThat(ctx.isRunning()).isTrue();
        }
    }

    @Test
    @DisplayName("修复验证：清空 spring.config.import 后，两种前缀的服务都能起来")
    void emptyImportWorksForBothPrefixStyles() {
        // MallIntegrationTest 现在设的是 spring.config.import=（空）。
        // 这条确认空值是合法的、且能盖住服务自己 application.yml 里写的前缀——
        // 无论那里是 configserver: 还是 optional:configserver:。
        //
        // 【为什么要专门测空值】"清空一个属性"看着无害，但如果 Spring 把空串
        // 当成一个要解析的位置，就会报 "Unable to load config data from ''"，
        // 那样 CI 会以另一种方式再挂一次。
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.config.import=",
                        "spring.cloud.config.enabled=false",
                        "spring.cloud.config.import-check.enabled=false",
                        "spring.cloud.consul.enabled=false")
                .run()) {
            assertThat(ctx.isRunning())
                    .as("清空 import 之后上下文应该正常起来")
                    .isTrue();
        }
        assertThat(received.get()).as("不该有任何 Config Server 请求").isZero();
    }
}

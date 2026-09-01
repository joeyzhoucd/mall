package com.mall.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedCircuitBreakerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 熔断配置的守护测试。
 *
 * <h3>为什么需要它</h3>
 * 熔断这一层有两个「编译通过、上线才炸」的风险，都不会有任何编译期或启动期报错：
 * <ol>
 *   <li>{@code resilience4j-spring-boot3} 这个包是按 Boot 3 命名的，而本项目跑在 Boot 4 上。
 *       它的自动配置能不能在 Boot 4 里正常装配，只能真的起一次上下文才知道。</li>
 *   <li><b>更要命的一条</b>：Spring Cloud CircuitBreaker 默认会把被保护的调用丢到
 *       独立线程池上执行（{@code disable-thread-pool} 默认 false）。
 *       而 {@code mall-order} 的 {@code OrderFeignConfig} 用
 *       {@code RequestContextHolder}（ThreadLocal）读当前请求的 Cookie 往下游透传，
 *       换了线程就读不到 —— 而那段代码在读不到时是<b>静默 return</b> 的，
 *       表现为下游认证失败，从拦截器到熔断器没有任何一处报错。
 *       所以「在调用方线程上执行」是个安全属性，必须钉死，不能靠注释提醒。</li>
 * </ol>
 *
 * <h3>为什么要有反向对照</h3>
 * 只断言「属性为 true 时跑在调用方线程」是不够的 —— 如果这个实现无论如何都在调用方
 * 线程上跑，那条断言会因为错误的原因通过，等于什么都没保护。所以下面还有一条
 * 把属性设成 false 的对照，确认它<b>确实会</b>换线程。两条一起才说明断言有效。
 */
class CircuitBreakerConfigTest {

    /**
     * 只加载熔断相关的自动配置，不需要容器，本机就能跑（本机没有 Docker）。
     * <p>
     * 两个都要：{@code CircuitBreakerAutoConfiguration} 来自 resilience4j 自己
     * （提供 {@code CircuitBreakerRegistry}，并负责把 {@code resilience4j.circuitbreaker.configs.*}
     * 绑上去），Spring Cloud 的 {@code Resilience4JAutoConfiguration} 在它之上再包一层
     * 框架无关的 {@code CircuitBreakerFactory} 抽象。只加后者会因为拿不到
     * {@code CircuitBreakerRegistry} 而起不来。
     */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
                    // TimeLimiterRegistry 也是 Resilience4JCircuitBreakerFactory 的构造参数，
                    // 即使我们把 TimeLimiter 关掉了，这个 bean 本身还是要在。
                    io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration.class,
                    Resilience4JAutoConfiguration.class));

    @Test
    @DisplayName("熔断的自动配置在 Boot 4 上能正常装配")
    void autoConfigurationLoadsOnBoot4() {
        runner.withPropertyValues("spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true")
                .run(context -> {
                    assertThat(context)
                            .as("上下文没起来，说明 resilience4j-spring-boot3 在 Boot 4 上装配不了")
                            .hasNotFailed();
                    assertThat(context).hasSingleBean(CircuitBreakerFactory.class);
                    assertThat(context.getBean(CircuitBreakerFactory.class))
                            .isInstanceOf(Resilience4JCircuitBreakerFactory.class);
                });
    }

    @Test
    @DisplayName("disable-thread-pool=true 时，被保护的调用跑在调用方线程上")
    void runsOnCallingThreadWhenThreadPoolDisabled() {
        runner.withPropertyValues("spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true")
                .run(context -> {
                    Thread caller = Thread.currentThread();
                    AtomicReference<Thread> executedOn = new AtomicReference<>();

                    context.getBean(CircuitBreakerFactory.class)
                            .create("threadCheck")
                            .run(() -> {
                                executedOn.set(Thread.currentThread());
                                return "ok";
                            });

                    assertThat(executedOn.get())
                            .as("调用被切到了别的线程 —— OrderFeignConfig 靠 ThreadLocal 透传的 "
                                    + "Cookie 会静默丢失，下游认证会失败且哪里都不报错")
                            .isSameAs(caller);
                });
    }

    @Test
    @DisplayName("反向对照：默认（不禁用线程池）时确实会换线程 —— 证明上一条断言有效")
    void runsOnAnotherThreadByDefault() {
        runner.withPropertyValues("spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=false")
                .run(context -> {
                    Thread caller = Thread.currentThread();
                    AtomicReference<Thread> executedOn = new AtomicReference<>();

                    context.getBean(CircuitBreakerFactory.class)
                            .create("threadCheckDefault")
                            .run(() -> {
                                executedOn.set(Thread.currentThread());
                                return "ok";
                            });

                    assertThat(executedOn.get())
                            .as("默认配置下也在调用方线程上跑的话，上面那条断言就是在空转 —— "
                                    + "它保护不了任何东西，得换一种验证方式")
                            .isNotSameAs(caller);
                });
    }

    /**
     * 告警规则和 Grafana 面板依赖的指标名。改这里之前先改 mall-deploy：
     * {@code charts/mall/files/alert-rules.yml} 和
     * {@code charts/mall/files/dashboards/resilience.json}。
     * <p>
     * Micrometer 导出到 Prometheus 时会把 {@code .} 换成 {@code _}，
     * TIMER 类型再加 {@code _seconds_count}/{@code _sum}/{@code _max}，
     * COUNTER 加 {@code _total}。所以下面的 {@code resilience4j.circuitbreaker.calls}
     * 在 Prometheus 里是 {@code resilience4j_circuitbreaker_calls_seconds_count}。
     */
    private static final List<String> METRICS_USED_BY_DASHBOARD = List.of(
            "resilience4j.circuitbreaker.state",
            "resilience4j.circuitbreaker.calls",
            "resilience4j.circuitbreaker.not.permitted.calls",
            "resilience4j.circuitbreaker.failure.rate",
            "resilience4j.circuitbreaker.slow.call.rate");

    @Test
    @DisplayName("面板和告警依赖的指标名确实存在（上游改名时这里先炸，而不是面板悄悄变空）")
    void metricNamesUsedByDashboardExist() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        CircuitBreakerRegistry cbRegistry = CircuitBreakerRegistry.ofDefaults();
        cbRegistry.circuitBreaker("nameCheck");
        TaggedCircuitBreakerMetrics.ofCircuitBreakerRegistry(cbRegistry).bindTo(registry);

        List<String> actual = registry.getMeters().stream()
                .map(m -> m.getId().getName())
                .distinct()
                .toList();

        assertThat(actual)
                .as("Resilience4j 注册的指标名变了。mall-deploy 里的告警规则和韧性面板"
                        + "都是按这些名字写的，不同步改的话它们会静默失效（查不到数据不报错）。")
                .containsAll(METRICS_USED_BY_DASHBOARD);

        // 状态是用 state 标签区分的，面板按 state="open" 过滤 —— 确认这个标签值存在
        assertThat(registry.find("resilience4j.circuitbreaker.state").tag("state", "open").gauge())
                .as("state 标签里没有 open —— 面板上『打开的电路数』那一格会永远是 0")
                .isNotNull();
    }

    @Test
    @DisplayName("失败率超阈值后电路真的会打开，并且打开后直接拒绝不再调用下游")
    void circuitActuallyOpens() {
        runner.withPropertyValues(
                        "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true",
                        // 用一组小参数让测试跑得快，语义和生产配置一致
                        "resilience4j.circuitbreaker.configs.default.sliding-window-type=COUNT_BASED",
                        "resilience4j.circuitbreaker.configs.default.sliding-window-size=4",
                        "resilience4j.circuitbreaker.configs.default.minimum-number-of-calls=4",
                        "resilience4j.circuitbreaker.configs.default.failure-rate-threshold=50",
                        "resilience4j.circuitbreaker.configs.default.wait-duration-in-open-state=60s",
                        "resilience4j.circuitbreaker.configs.default.automatic-transition-from-open-to-half-open-enabled=false")
                .run(context -> {
                    CircuitBreakerFactory<?, ?> factory = context.getBean(CircuitBreakerFactory.class);
                    CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry.class);

                    // 先打满窗口的失败调用。有 fallback，所以这里不会抛出来。
                    for (int i = 0; i < 4; i++) {
                        factory.create("openCheck").run(
                                () -> { throw new IllegalStateException("下游炸了"); },
                                throwable -> "fallback");
                    }

                    CircuitBreaker breaker = registry.circuitBreaker("openCheck");
                    assertThat(breaker.getState())
                            .as("失败率 100% 且达到最小调用数之后电路仍未打开，熔断等于没生效")
                            .isEqualTo(CircuitBreaker.State.OPEN);

                    // 电路打开之后，被保护的代码【不应该再被执行】——
                    // 这才是"快速失败、给下游喘息时间"的实际含义。
                    AtomicReference<Boolean> downstreamCalled = new AtomicReference<>(false);
                    String result = factory.create("openCheck").run(
                            () -> { downstreamCalled.set(true); return "不该走到这"; },
                            throwable -> "rejected");

                    assertThat(downstreamCalled.get())
                            .as("电路已打开却仍然调用了下游 —— 那就没起到保护作用")
                            .isFalse();
                    assertThat(result).isEqualTo("rejected");
                });
    }
}

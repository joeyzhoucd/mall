package com.mall.common.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ActuatorObservationFilterAutoConfiguration} 的守护测试。
 *
 * <h3>为什么这些断言值得写</h3>
 * 这个过滤器的失效方式是<b>安静的</b>：谓词写错了不会报错，只是继续记录 actuator 的链路 ——
 * 表现为 Tempo 里还是一堆噪声，而没有任何地方提示「过滤器没生效」。
 * 反过来如果谓词过于宽松（比如漏判观测名），会把<b>业务请求的指标一起干掉</b>，
 * 那更糟：Grafana 面板整片变空，而应用一切正常。
 * 所以正反两个方向都要断言。
 */
class ActuatorObservationFilterTest {

    private static final String HTTP = "http.server.requests";

    // ---------------------------------------------------------------- Servlet

    private final WebApplicationContextRunner servletRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActuatorObservationFilterAutoConfiguration.class));

    @Test
    @DisplayName("Servlet 应用里会注册过滤器 bean")
    void registersPredicateInServletApp() {
        servletRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ObservationPredicate.class);
        });
    }

    @Test
    @DisplayName("Servlet：/actuator/** 被排除，业务路径放行")
    void servletPredicateFiltersActuatorOnly() {
        servletRunner.run(context -> {
            ObservationPredicate predicate = context.getBean(ObservationPredicate.class);

            assertThat(predicate.test(HTTP, servletContext("/actuator/health")))
                    .as("/actuator/health 应被排除 —— 它占了实测链路噪声的 68%")
                    .isFalse();
            assertThat(predicate.test(HTTP, servletContext("/actuator/prometheus")))
                    .as("/actuator/prometheus 应被排除")
                    .isFalse();

            assertThat(predicate.test(HTTP, servletContext("/product/skuinfo/info/1")))
                    .as("业务路径被误排除的话，Grafana 面板会整片变空而应用一切正常 —— 最糟的失效方式")
                    .isTrue();
            assertThat(predicate.test(HTTP, servletContext("/")))
                    .as("根路径不该被排除")
                    .isTrue();
        });
    }

    @Test
    @DisplayName("只过滤 HTTP 服务端观测，其它观测（JDBC / Feign / MQ）一律放行")
    void doesNotTouchOtherObservationTypes() {
        servletRunner.run(context -> {
            ObservationPredicate predicate = context.getBean(ObservationPredicate.class);

            // 观测名不是 http.server.requests 时，即使上下文长得像 actuator 请求也要放行 ——
            // 否则会误伤 jdbc / feign 客户端 / rabbitmq 监听那几类观测。
            assertThat(predicate.test("jdbc.connections", servletContext("/actuator/health"))).isTrue();
            assertThat(predicate.test("http.client.requests", servletContext("/actuator/health"))).isTrue();
            assertThat(predicate.test("spring.rabbit.listener", new Observation.Context())).isTrue();
        });
    }

    @Test
    @DisplayName("上下文类型不认识时放行，不能误杀")
    void unknownContextTypeIsAllowed() {
        servletRunner.run(context -> {
            ObservationPredicate predicate = context.getBean(ObservationPredicate.class);
            // 即使观测名对得上，拿不到路径也必须放行 —— 宁可多记录，不可少记录
            assertThat(predicate.test(HTTP, new Observation.Context())).isTrue();
        });
    }

    // WebFlux 分支的测试在 mall-gateway 那边（ActuatorObservationFilterReactiveTest）——
    // mall-common 的测试 classpath 上没有 webflux，ReactiveWebApplicationContextRunner
    // 建不出响应式上下文，条件不匹配，测了也是假通过。
    // 各自在真正有那套依赖的模块里测，比给 mall-common 加一个只为测试用的依赖干净。

    // ------------------------------------------------------------- 非 Web 应用

    @Test
    @DisplayName("非 Web 应用里不注册 —— 避免给不需要的上下文平白加个 bean")
    void notRegisteredInNonWebApp() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ActuatorObservationFilterAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ObservationPredicate.class);
                });
    }

    // ---------------------------------------------------------------- helpers

    private static ServerRequestObservationContext servletContext(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return new ServerRequestObservationContext(request, new MockHttpServletResponse());
    }
}

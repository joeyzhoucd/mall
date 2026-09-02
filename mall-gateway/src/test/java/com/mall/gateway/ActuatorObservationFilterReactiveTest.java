package com.mall.gateway;

import com.mall.common.config.ActuatorObservationFilterAutoConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ActuatorObservationFilterAutoConfiguration} 的 <b>WebFlux 分支</b>守护测试。
 *
 * <h3>为什么这个测试在 mall-gateway 而不是 mall-common</h3>
 * 那个自动配置拆成了 Servlet / Reactive 两个嵌套配置，因为承载请求的上下文类型
 * 在两边是不同的类（包名不同）。mall-common 的测试 classpath 上<b>没有 webflux</b>，
 * {@code ReactiveWebApplicationContextRunner} 建不出响应式上下文，
 * {@code @ConditionalOnWebApplication(REACTIVE)} 不匹配 —— 在那边写这个测试只会是假通过。
 * <p>
 * 与其给 mall-common 加一个纯为测试的 webflux 依赖，不如让每个分支在真正有那套依赖的
 * 模块里测。网关是本项目唯一的 WebFlux 应用，这里就是它的家。
 *
 * <h3>网关尤其需要这个过滤器</h3>
 * 网关自己的 {@code /actuator} 是<b>公网可达</b>的（实测 {@code Host: mall.com} 能打到），
 * 被外部探活打得最频繁；同时 K8s 双探针 + Consul + Prometheus 也在持续打它。
 * 不过滤的话这些全都会变成链路和指标里的噪声。
 */
class ActuatorObservationFilterReactiveTest {

    private static final String HTTP = "http.server.requests";

    private final ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActuatorObservationFilterAutoConfiguration.class));

    @Test
    @DisplayName("WebFlux 应用里会注册过滤器 bean")
    void registersPredicateInReactiveApp() {
        runner.run(context -> {
            assertThat(context)
                    .as("响应式上下文里没装配上 —— 网关的 actuator 噪声就过滤不掉")
                    .hasNotFailed();
            assertThat(context).hasSingleBean(ObservationPredicate.class);
        });
    }

    @Test
    @DisplayName("WebFlux：/actuator/** 被排除，业务路径放行")
    void reactivePredicateFiltersActuatorOnly() {
        runner.run(context -> {
            ObservationPredicate predicate = context.getBean(ObservationPredicate.class);

            assertThat(predicate.test(HTTP, reactiveContext("/actuator/health"))).isFalse();
            assertThat(predicate.test(HTTP, reactiveContext("/actuator/prometheus"))).isFalse();

            assertThat(predicate.test(HTTP, reactiveContext("/api/product/list")))
                    .as("业务路径被误排除的话，网关的 RED 指标会整片消失而服务一切正常")
                    .isTrue();
            assertThat(predicate.test(HTTP, reactiveContext("/"))).isTrue();
        });
    }

    @Test
    @DisplayName("只过滤 HTTP 服务端观测；上下文不认识时放行")
    void doesNotOverReach() {
        runner.run(context -> {
            ObservationPredicate predicate = context.getBean(ObservationPredicate.class);
            // 观测名不对 —— 即使路径像 actuator 也放行，别误伤别的观测
            assertThat(predicate.test("http.client.requests", reactiveContext("/actuator/health"))).isTrue();
            // 拿不到路径时放行：宁可多记录，不可少记录
            assertThat(predicate.test(HTTP, new Observation.Context())).isTrue();
        });
    }

    private static ServerRequestObservationContext reactiveContext(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
        // 第三个参数是 exchange attributes，传 null 会在构造时 NPE（实测），给个空 Map。
        return new ServerRequestObservationContext(request, new MockServerHttpResponse(), new HashMap<>());
    }
}

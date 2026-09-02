package com.mall.common.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把 {@code /actuator/**} 的 HTTP 请求排除出观测（既不产生链路 span，也不进 http_server_requests 指标）。
 *
 * <h3>为什么需要</h3>
 * 链路采样率调到 1.0 之后实测：200 条链路里 <b>86% 是噪声</b> ——
 * {@code /actuator/health} 占 68%、{@code /actuator/prometheus} 占 18%。
 * 来源是 12 个服务 × （Consul 每 10 秒 + K8s readiness 每 10 秒 + liveness 每 20 秒
 * + Prometheus 每 15 秒）。业务链路被埋在里面，得靠 TraceQL 过滤才看得见，
 * 而且这些噪声还在白白占 Tempo 的存储（保留期只有 72 小时，本来就紧张）。
 *
 * <h3>为什么只能用代码，不能用配置</h3>
 * 查过配置元数据：{@code management.observations.enable.<name>} 是按<b>观测名</b>
 * 整体开关的，而健康检查和业务请求用的是同一个观测名 {@code http.server.requests}，
 * 关掉就把业务请求的指标一起关了。要按 URI 过滤只有 {@link ObservationPredicate} 这条路。
 *
 * <h3>为什么拆成两个嵌套类</h3>
 * 承载请求的上下文类型在 Servlet 和 WebFlux 下是<b>两个不同的类</b>
 * （{@code org.springframework.http.server.observation.ServerRequestObservationContext}
 * 和 {@code ...http.server.reactive.observation.ServerRequestObservationContext}，
 * 名字一样、包不同）。mall-common 同时被 11 个 Servlet 服务和 WebFlux 的 mall-gateway 依赖，
 * 外层类的方法签名里不能出现任何一边独有的类型 —— 否则另一边加载这个自动配置时会
 * {@code NoClassDefFoundError}。
 * <p>
 * 把类型引用关在嵌套的 {@code @Configuration} 里、由 {@code @ConditionalOnClass} 决定是否加载，
 * 是本项目既有的约定（{@code AutoConfigurationSignatureTest} 会检查外层类的方法签名，
 * 见 {@code MallOpenApiAutoConfiguration} 的同样写法）。
 *
 * <h3>影响范围</h3>
 * 只影响<b>观测</b>（链路 + http_server_requests 指标），<b>不影响</b>：
 * <ul>
 *   <li>{@code /actuator/prometheus} 端点本身 —— Prometheus 照常能抓到全部指标；</li>
 *   <li>K8s 探针和 Consul 健康检查 —— 它们只是普通 HTTP 请求，照常响应；</li>
 *   <li>访问日志。</li>
 * </ul>
 * 也就是说，代价仅仅是「看不到健康检查自己的耗时指标」，那本来也没人看。
 */
@AutoConfiguration
public class ActuatorObservationFilterAutoConfiguration {

    /** actuator 的默认 base path。改了 {@code management.endpoints.web.base-path} 的话这里也要跟着改。 */
    static final String ACTUATOR_PREFIX = "/actuator";

    /** 只对 HTTP 服务端观测生效；其它观测（JDBC、Feign 客户端、MQ 监听）一律放行。 */
    static final String HTTP_SERVER_OBSERVATION = "http.server.requests";

    static boolean isActuatorPath(String path) {
        return path != null && path.startsWith(ACTUATOR_PREFIX);
    }

    /**
     * Servlet 侧（11 个业务服务）。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(org.springframework.http.server.observation.ServerRequestObservationContext.class)
    static class ServletConfiguration {

        @Bean
        ObservationPredicate skipActuatorServletObservations() {
            return (name, context) -> {
                if (!HTTP_SERVER_OBSERVATION.equals(name)) {
                    return true;
                }
                if (context instanceof org.springframework.http.server.observation.ServerRequestObservationContext ctx) {
                    // getCarrier() 在观测【创建时】就可用，不像低基数标签要等观测结束才填，
                    // 所以这里能拿到路径。这是必须按上下文类型判断、而不能读 KeyValue 的原因。
                    return !isActuatorPath(ctx.getCarrier().getRequestURI());
                }
                return true;
            };
        }
    }

    /**
     * WebFlux 侧（mall-gateway）。
     * <p>
     * 网关其实也值得排除：它自己的 /actuator 被外部探活打得最频繁。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    @ConditionalOnClass(org.springframework.http.server.reactive.observation.ServerRequestObservationContext.class)
    static class ReactiveConfiguration {

        @Bean
        ObservationPredicate skipActuatorReactiveObservations() {
            return (name, context) -> {
                if (!HTTP_SERVER_OBSERVATION.equals(name)) {
                    return true;
                }
                if (context instanceof org.springframework.http.server.reactive.observation.ServerRequestObservationContext ctx) {
                    return !isActuatorPath(ctx.getCarrier().getPath().value());
                }
                return true;
            };
        }
    }
}

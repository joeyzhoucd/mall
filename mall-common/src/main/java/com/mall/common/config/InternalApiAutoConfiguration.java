package com.mall.common.config;

import com.mall.common.security.InternalTokenInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 服务间内部令牌：Feign 调用方自动带 {@code X-Internal-Token}，提供方对 {@code @InternalApi} 接口校验。
 *
 * <p>配置（都走环境变量，集群里来自 SealedSecret mall-internal-token）：
 * <ul>
 *   <li>{@code mall.internal.token}（INTERNAL_TOKEN）默认空。report 模式下允许为空（指标记 unconfigured），
 *       所以 Dockerfile 的 CDS 训练不用补参数；enforce 模式下为空则启动失败 —— 不存在「退回某个公开默认值」。</li>
 *   <li>{@code mall.internal.enforce}（INTERNAL_ENFORCE）默认 false = report 模式，见 InternalTokenInterceptor。</li>
 * </ul>
 *
 * <p>【必须走 AutoConfiguration.imports】同包的 FeignTraceConfig 是普通 @Configuration，
 * 只有显式 scanBasePackages 了 com.mall.common 的服务才会加载它 —— 大部分服务里它根本没生效。
 * 这里如果也那样写，就是「只有部分服务带令牌」，切 enforce 时那部分调用全挂。
 *
 * <p>两段各自是嵌套静态类、条件挂在类上：EagerConnectionWarmup 的教训，条件挂在方法上时，
 * 方法签名里引用的类型不存在会让整个外层类加载失败。
 */
@AutoConfiguration
public class InternalApiAutoConfiguration {

    static final String TOKEN = "mall.internal.token";
    static final String ENFORCE = "mall.internal.enforce";

    private static final Logger log = LoggerFactory.getLogger(InternalApiAutoConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignSide {

        @Bean
        feign.RequestInterceptor internalTokenRequestInterceptor(Environment env) {
            String token = env.getProperty(TOKEN, "");
            if (token.isBlank()) {
                log.warn("{} 未配置：本服务发出的 Feign 请求不带内部令牌", TOKEN);
            }
            return template -> {
                if (!token.isBlank()) {
                    template.header(InternalTokenInterceptor.HEADER, token);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    static class ServerSide {

        @Bean
        InternalTokenInterceptor internalTokenInterceptor(Environment env, ObjectProvider<MeterRegistry> registries) {
            boolean enforce = env.getProperty(ENFORCE, Boolean.class, false);
            // 不用 @ConditionalOnBean(MeterRegistry)：见 BusinessMetricsAutoConfiguration 的说明
            MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
            log.info("内部接口校验模式: {}", enforce ? "enforce（不通过返回 401）" : "report（不通过只记录）");
            return new InternalTokenInterceptor(env.getProperty(TOKEN, ""), enforce, registry);
        }

        @Bean
        WebMvcConfigurer internalTokenWebMvcConfigurer(InternalTokenInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor);
                }
            };
        }
    }
}

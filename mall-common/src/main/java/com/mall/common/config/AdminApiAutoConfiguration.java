package com.mall.common.config;

import com.mall.common.security.AdminTokenInterceptor;
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
 * 服务端默认拒绝（管理端 JWT），见 {@link AdminTokenInterceptor}。
 *
 * <p>配置：
 * <ul>
 *   <li>{@code mall.admin.jwt.public-key}（JWT_PUBLIC_KEY）—— RS256 公钥。各服务只能验、不能签：
 *       私钥只在 mall-admin（HS256 的共享密钥发给每个服务，等于每个服务都能签发管理端令牌）。
 *       默认值为空（report 模式下记 unconfigured）。</li>
 *   <li>{@code mall.admin-api.enforce}（ADMIN_API_ENFORCE）默认 false = report。</li>
 * </ul>
 * 走 AutoConfiguration.imports，原因同 InternalApiAutoConfiguration（普通 @Configuration 只在
 * 扫描了 com.mall.common 的服务里生效）。只对 servlet 应用生效：网关是 WebFlux，它自己有 AdminAuthFilter。
 */
@AutoConfiguration
public class AdminApiAutoConfiguration {

    static final String PUBLIC_KEY = "mall.admin.jwt.public-key";
    static final String ENFORCE = "mall.admin-api.enforce";

    private static final Logger log = LoggerFactory.getLogger(AdminApiAutoConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerInterceptor")
    static class ServerSide {

        @Bean
        AdminTokenInterceptor adminTokenInterceptor(Environment env, ObjectProvider<MeterRegistry> registries) {
            boolean enforce = env.getProperty(ENFORCE, Boolean.class, false);
            MeterRegistry registry = registries.getIfAvailable(SimpleMeterRegistry::new);
            log.info("管理端接口（默认拒绝）校验模式: {}", enforce ? "enforce（不通过返回 401）" : "report（不通过只记录）");
            return new AdminTokenInterceptor(env.getProperty(PUBLIC_KEY, ""), enforce, registry);
        }

        @Bean
        WebMvcConfigurer adminTokenWebMvcConfigurer(AdminTokenInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor);
                }
            };
        }
    }
}

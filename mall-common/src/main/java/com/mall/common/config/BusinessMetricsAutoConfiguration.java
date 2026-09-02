package com.mall.common.config;

import com.mall.common.metrics.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 把 {@link BusinessMetrics} 装进容器，供各业务服务注入。
 * <p>
 * 条件是「容器里有 MeterRegistry」—— 所有服务都通过 mall-common 带了
 * actuator + micrometer-registry-prometheus，所以实际上处处可用；
 * 加这个条件是为了让没有指标注册表的场景（比如某些测试上下文）能干净地跳过，
 * 而不是启动失败。
 */
@AutoConfiguration
public class BusinessMetricsAutoConfiguration {

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    BusinessMetrics businessMetrics(MeterRegistry registry) {
        return new BusinessMetrics(registry);
    }
}

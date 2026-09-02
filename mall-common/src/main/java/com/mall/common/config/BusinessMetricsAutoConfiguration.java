package com.mall.common.config;

import com.mall.common.metrics.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 把 {@link BusinessMetrics} 装进容器，供各业务服务注入。
 *
 * <h3>为什么不用 {@code @ConditionalOnBean(MeterRegistry.class)}</h3>
 * 最初就是那么写的，看起来很合理：「容器里有 MeterRegistry 才创建」。
 * 它让三个业务服务的 Spring 上下文全都起不来，被 CI 的集成测试挡下。
 * <p>
 * 原因：自动配置上的 {@code @ConditionalOnBean} 是在<b>该自动配置类被处理的那一刻</b>
 * 求值的，而自动配置的先后由排序决定；没有 before/after 声明时按全限定类名排序，
 * {@code com.mall.*} 排在 {@code org.springframework.*} 前面。于是条件求值时
 * Boot 的 metrics 自动配置还没跑，MeterRegistry 还不存在，bean 不创建 ——
 * 而 mall-order / mall-coupon / mall-ware 里都是必需的字段注入。
 * <p>
 * 现在改成 {@link ObjectProvider}：参数在<b>bean 创建时</b>才解析，那时容器已经
 * 知道所有 bean 定义，和自动配置排序无关。而且这个 bean <b>总是</b>产出，
 * 所以调用方的必需注入不可能失败 —— 一个观测功能不该有能力把业务服务拖down。
 * <p>
 * 守这件事的是 {@code BusinessMetricsAutoConfigurationTest}，它用
 * {@code ApplicationContextRunner} + {@code AutoConfigurations.of(...)}
 * 复现真实的排序（手动注册 @Configuration 会绕过排序，测试就没意义了）。
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class BusinessMetricsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetricsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    BusinessMetrics businessMetrics(ObjectProvider<MeterRegistry> registries) {
        // getIfAvailable 会遵循 @Primary：真实服务里同时存在 Prometheus 和 Simple
        // 两个注册表时，Boot 的 CompositeMeterRegistryAutoConfiguration 会建一个
        // @Primary 的组合注册表，所以解析是唯一的。
        // 刻意不用 getIfUnique —— 那会在真的有歧义时静默返回 null，退化成下面那个
        // 没人抓取的兜底注册表，表现为「代码在跑、Prometheus 里什么都没有」。
        // 真有歧义就该在启动时大声失败。
        MeterRegistry registry = registries.getIfAvailable(() -> {
            // 走到这里说明上下文里完全没有 metrics 自动配置（比如某些切片测试上下文）。
            // 用一个丢弃式注册表让上下文能起来，但必须留下痕迹：
            // 如果哪天生产环境走到这一分支，所有业务指标都会静默消失。
            log.warn("容器里没有 MeterRegistry，BusinessMetrics 退化到一个不会被抓取的注册表。"
                    + "如果这条出现在真实服务的日志里，说明所有业务指标都不会进 Prometheus。");
            return new SimpleMeterRegistry();
        });
        return new BusinessMetrics(registry);
    }
}

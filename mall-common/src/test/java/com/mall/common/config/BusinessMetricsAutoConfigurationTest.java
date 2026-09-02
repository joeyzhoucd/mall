package com.mall.common.config;

import com.mall.common.metrics.BusinessMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * {@link BusinessMetricsAutoConfiguration} 必须在真实的自动配置排序下也能产出 bean。
 *
 * <h3>这个测试是为一次真实的 CI 失败立的哨</h3>
 * 最初的写法是 {@code @Bean @ConditionalOnBean(MeterRegistry.class)}，看起来很合理：
 * 「容器里有 MeterRegistry 才创建」。但自动配置的 {@code @ConditionalOnBean} 是在
 * <b>该自动配置类被处理的那一刻</b>求值的，而自动配置的先后由排序决定，
 * 排序在没有 before/after 声明时按全限定类名 —— {@code com.mall.*} 排在
 * {@code org.springframework.*} 前面。于是条件求值时 MeterRegistry 还没注册，
 * bean 不创建；而 mall-order / mall-coupon / mall-ware 里是必需的字段注入，
 * 于是三个服务的 Spring 上下文直接起不来。
 * <p>
 * 单元测试全绿、{@code helm template} 全绿，只有真正启上下文的集成测试会挂 ——
 * 又一个「编译通过但完全不工作」。
 * <p>
 * 现在的写法用 {@code ObjectProvider} 在<b>bean 创建时</b>解析注册表
 * （那时容器已经知道所有 bean 定义，与排序无关），并且总是产出 bean，
 * 所以调用方的必需注入永远能满足。下面第一条就是守这件事的。
 */
class BusinessMetricsAutoConfigurationTest {

    /** 只装我们自己的自动配置：模拟「没有任何 metrics 自动配置」的最小上下文。 */
    private final ApplicationContextRunner bare = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BusinessMetricsAutoConfiguration.class));

    /**
     * 带上 Boot 真实的 metrics 自动配置。用 AutoConfigurations.of 而不是手动
     * 注册 @Configuration，是因为前者会套用 Boot 的自动配置排序规则 ——
     * 排序正是这里要测的东西，手动注册会绕过它，测试就失去意义了。
     */
    private final ApplicationContextRunner withMetrics = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    SimpleMetricsExportAutoConfiguration.class,
                    BusinessMetricsAutoConfiguration.class));

    @Test
    @DisplayName("真实排序下必须产出 BusinessMetrics bean（挂过的就是这条）")
    void beanExistsUnderRealAutoConfigurationOrdering() {
        withMetrics.run(ctx -> {
            org.assertj.core.api.Assertions.assertThat(ctx).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BusinessMetrics.class);
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(MeterRegistry.class);
        });
    }

    @Test
    @DisplayName("即使上下文里完全没有 MeterRegistry，也要产出 bean（调用方是必需注入）")
    void beanExistsEvenWithoutAnyMeterRegistry() {
        // 这不是「顺便也支持一下」。mall-order / mall-coupon / mall-ware 里都是
        // 必需的字段注入，bean 缺失就是上下文启动失败。宁可退化成一个丢弃指标的
        // 注册表，也不能让一个观测功能把业务服务拖down。
        bare.run(ctx -> {
            org.assertj.core.api.Assertions.assertThat(ctx).hasNotFailed();
            org.assertj.core.api.Assertions.assertThat(ctx).hasSingleBean(BusinessMetrics.class);
        });
    }

    @Test
    @DisplayName("用的是容器里那个 MeterRegistry，不是自己新建的")
    void usesTheContextMeterRegistry() {
        withMetrics.run(ctx -> {
            BusinessMetrics metrics = ctx.getBean(BusinessMetrics.class);
            MeterRegistry registry = ctx.getBean(MeterRegistry.class);
            metrics.success("test.wiring");
            // 指标必须落在容器的注册表里 —— 否则埋点全都进了一个没人抓取的注册表，
            // 表现为「代码在跑、Prometheus 里什么都没有」。
            org.assertj.core.api.Assertions.assertThat(
                            registry.find(BusinessMetrics.METRIC_NAME).counter())
                    .as("指标没落在容器的 MeterRegistry 上")
                    .isNotNull();
        });
    }
}

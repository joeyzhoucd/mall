package com.mall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 守住启动预热的两条性质。
 *
 * <h3>为什么这条测试的重点是「失败也不能影响启动」</h3>
 * 预热本身失败是可以接受的（那说明某个中间件此刻不可用，交给探针和重试处理）。
 * 不可接受的是<b>因为预热失败而让一个功能完好的服务起不来</b> —— 那会把
 * 「中间件抖了一下」放大成「所有服务 crashloop」，比不做预热糟糕得多。
 * <p>
 * 这个风险很容易在后续重构里被引入：把 warmup 里的 catch 去掉、或者改成抛异常，
 * 编译和启动（中间件正常时）都看不出问题，只在中间件真出问题的那天才暴露 ——
 * 而那正是最不能雪上加霜的时刻。
 *
 * <h3>为什么手动调 ApplicationRunner</h3>
 * {@link ApplicationContextRunner} 只构建上下文，不会执行 {@code SpringApplication}
 * 的生命周期，所以不会自动调用 ApplicationRunner。这里显式取出来调用，
 * 反而让「跑了什么」更明确。
 */
class EagerConnectionWarmupTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(EagerConnectionWarmup.class));

    @Test
    @DisplayName("正常情况下会真的去取一次连接")
    void touchesDataSourceOnStartup() {
        runner.withUserConfiguration(HealthyDataSourceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            invokeAllRunners(context.getBeansOfType(ApplicationRunner.class).values());
            assertThat(HealthyDataSourceConfig.calls.get())
                    .as("没有取过连接，预热等于没做 —— 连接池仍会在第一个请求时才初始化")
                    .isEqualTo(1);
        });
    }

    @Test
    @DisplayName("预热失败时只警告，不能让启动失败")
    void warmupFailureDoesNotBreakStartup() {
        runner.withUserConfiguration(BrokenDataSourceConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            // 关键断言：runner 抛出去的话，真实启动会直接失败。
            invokeAllRunners(context.getBeansOfType(ApplicationRunner.class).values());
        });
    }

    @Test
    @DisplayName("mall.warmup.enabled=false 时整个自动配置不生效")
    void canBeDisabled() {
        runner.withPropertyValues("mall.warmup.enabled=false")
                .withUserConfiguration(HealthyDataSourceConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(ApplicationRunner.class));
    }

    private static void invokeAllRunners(Iterable<ApplicationRunner> runners) throws Exception {
        ApplicationArguments noArgs = mock(ApplicationArguments.class);
        for (ApplicationRunner r : runners) {
            r.run(noArgs);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HealthyDataSourceConfig {

        static final AtomicInteger calls = new AtomicInteger();

        @Bean
        DataSource dataSource() throws SQLException {
            calls.set(0);
            Connection connection = mock(Connection.class);
            when(connection.isValid(anyInt())).thenReturn(true);
            DataSource ds = mock(DataSource.class);
            when(ds.getConnection()).thenAnswer(invocation -> {
                calls.incrementAndGet();
                return connection;
            });
            return ds;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class BrokenDataSourceConfig {

        @Bean
        DataSource dataSource() throws SQLException {
            DataSource ds = mock(DataSource.class);
            // 模拟「数据库此刻连不上」——这是预热失败最常见的原因。
            when(ds.getConnection()).thenThrow(new SQLException("模拟数据库不可用"));
            return ds;
        }
    }
}

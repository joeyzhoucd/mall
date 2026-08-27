package com.mall.common.config;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.DataSourceUnwrapper;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 JDBC 链路埋点的三条性质，其中第二条是真正的回归风险。
 *
 * <h3>为什么「Hikari 指标还在」这条最重要</h3>
 * 这个自动配置会用 JDK 动态代理把 DataSource 包一层。Boot 的连接池指标
 * （{@code hikaricp_connections_*}）是靠 {@code DataSourceUnwrapper} 从 DataSource
 * 里掏出 {@code HikariDataSource} 才能采集的 —— 如果代理挡住了这条路，指标会
 * <b>安静地消失</b>：应用照常跑、链路 span 照常有，只是连接池那一组指标不再上报。
 * <p>
 * 这不是假想的损失。2026-08-27 排查秒杀慢在哪时，正是靠
 * {@code hikaricp_connections_timeout_total} 恒为 0 排除了「数据库连接池是瓶颈」
 * 这个方向，才把注意力转到 CPU 排队上。为了看清 SQL 而弄丢连接池指标，
 * 是典型的拆东墙补西墙。
 */
class JdbcObservationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JdbcObservationAutoConfiguration.class))
            .withUserConfiguration(HikariConfig.class);

    @Test
    @DisplayName("DataSource 被包成带 observation 监听的代理")
    void wrapsDataSourceWithObservationProxy() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DataSourceObservationListener.class);
            assertThat(context.getBean(DataSource.class))
                    .as("DataSource 没有被包代理，JDBC 不会产生任何 span")
                    .isInstanceOf(ProxyDataSource.class);
        });
    }

    @Test
    @DisplayName("包代理之后 Boot 仍能 unwrap 出 HikariDataSource（连接池指标不丢）")
    void hikariMetricsSurviveTheProxy() {
        runner.run(context -> {
            DataSource proxied = context.getBean(DataSource.class);
            // 前置确认：确实是代理，否则这条测试等于什么都没验证。
            assertThat(proxied).isInstanceOf(ProxyDataSource.class);

            // 必须用 Boot 实际使用的那个【三参】重载。
            // 第一版这里写的是两参的 unwrap(ds, HikariDataSource.class)，它返回 null，
            // 于是测试报「代理挡住了解包」—— 而 Boot 根本不走那条路。
            // 反编译 DataSourcePoolMetricsAutoConfiguration$HikariDataSourceMetricsConfiguration
            // $HikariDataSourceMeterBinder 才看清它调的是：
            //   DataSourceUnwrapper.unwrap(ds, HikariConfigMXBean.class, HikariDataSource.class)
            // 教训：断言要对着【被测系统真正执行的调用】，对着一个形似的 API 断言，
            // 失败和通过都不能说明问题 —— 这条测试第一版就是个假警报。
            HikariDataSource unwrapped =
                    DataSourceUnwrapper.unwrap(proxied, HikariConfigMXBean.class, HikariDataSource.class);
            assertThat(unwrapped)
                    .as("代理挡住了 Boot 的解包路径，hikaricp_connections_* 这一组指标会安静地消失")
                    .isNotNull();
            assertThat(unwrapped).isSameAs(context.getBean(HikariConfig.class).hikari);
        });
    }

    @Test
    @DisplayName("mall.observability.jdbc.enabled=false 时完全不介入")
    void canBeDisabled() {
        runner.withPropertyValues("mall.observability.jdbc.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(DataSourceObservationListener.class);
            assertThat(context.getBean(DataSource.class))
                    .as("关掉之后不应该还有代理 —— 代理层的开销是每次调用都有的")
                    .isNotInstanceOf(ProxyDataSource.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class HikariConfig {

        final HikariDataSource hikari = new HikariDataSource();

        HikariConfig() {
            // 不需要能真的连上：unwrap 和包代理都不碰数据库。
            // 刻意不设 jdbcUrl，避免任何一步意外去建连接。
            hikari.setPoolName("test-pool");
        }

        @Bean
        DataSource dataSource() {
            return hikari;
        }
    }
}

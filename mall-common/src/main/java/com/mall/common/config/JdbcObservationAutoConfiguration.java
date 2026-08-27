package com.mall.common.config;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.observation.tracing.ConnectionTracingObservationHandler;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.HikariJdbcObservationFilter;
import net.ttddyy.observation.tracing.QueryTracingObservationHandler;
import net.ttddyy.observation.tracing.ResultSetTracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import javax.sql.DataSource;

/**
 * 给 JDBC 加链路 span，让「这个请求慢」能细化到「哪条 SQL 慢 / 卡在拿连接上」。
 *
 * <h3>为什么需要它（2026-08-27 压测实测）</h3>
 * 压测里一条耗时 7.4 秒的秒杀抢购链路，在 Tempo 里<b>只有 1 个 span</b>。
 * 链路追踪只能告诉我「这个请求慢」，完全无法回答「慢在哪一步」——
 * 定位只能退回到「把每个依赖单独量一遍」（结论是 mall-member 1–52ms、
 * HikariCP 零超时，所以时间全在自己的 CPU 排队上）。那正是有了链路追踪
 * 本该不必再做的手工活。
 *
 * <h3>为什么不用官方的 Spring Boot starter</h3>
 * {@code net.ttddyy.observation:datasource-micrometer-spring-boot} 到 1.2.1（当前最新）
 * 仍然引用 Boot 3 的包名：
 * <ul>
 *   <li>{@code org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration}</li>
 *   <li>{@code org.springframework.boot.actuate.autoconfigure.tracing.ConditionalOnEnabledTracing}</li>
 *   <li>{@code org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration}</li>
 * </ul>
 * 这三个在 Boot 4.1.1 里<b>都不存在</b>（分别挪到了
 * {@code micrometer.observation.autoconfigure.*} / {@code micrometer.tracing.autoconfigure.*} /
 * {@code jdbc.autoconfigure.*}）。
 * <p>
 * 更要紧的是<b>它的失败方式可能是静默的</b>：JVM 遇到无法解析的注解类型会直接把注解丢掉，
 * 于是 {@code @ConditionalOnEnabledTracing} 等于没写，条件判断悄悄失效 —— 又是一个
 * 「编译通过、启动正常、行为不对」。所以这里只用它的<b>核心库</b>
 * （{@code datasource-micrometer}，用 javap 逐个 class 扫过，零个 {@code org/springframework}
 * 引用，只依赖 micrometer-tracing / datasource-proxy / HikariCP），自己按 Boot 4 的包名接线。
 *
 * <h3>handler 的 @Order 不是可选的</h3>
 * Boot 会把所有 {@code TracingObservationHandler} 收成一个「首个匹配者胜出」的组合
 * （{@code FirstMatchingCompositeObservationHandler}）。Boot 自己的
 * {@code DefaultTracingObservationHandler} 匹配<b>任何</b> context，如果它排在前面，
 * JDBC 的 context 会被它先接走 —— span 还是有，但 DB 相关的标签（SQL、连接池、行数）
 * 全部丢失，而且不会有任何报错。
 * <p>
 * Boot 给它的 order 是 {@code LOWEST_PRECEDENCE - 1000}，官方 starter 给 JDBC handler 的是
 * {@code LOWEST_PRECEDENCE - 2000}（数字更小 = 优先级更高）。这里沿用同样的相对关系，
 * 而不是写死那个具体数字 —— Boot 改了常量的话相对关系仍然成立。
 *
 * <h3>代价：这不是零开销的</h3>
 * 实现方式是用 JDK 动态代理把 DataSource 包一层，<b>每一次取连接和每一条 SQL 都多走一层代理</b>。
 * 而压测已经证明 mall-coupon 在 500m CPU 下是 CPU 受限的（冷启动时 CPU 100%），
 * 所以这个开销不能当成不存在。因此：
 * <ul>
 *   <li>做成可关（{@code mall.observability.jdbc.enabled=false}），出问题时能一键退回；</li>
 *   <li>开启后<b>必须用同一套压测脚本复量一遍</b>，把代价写成数字而不是「应该不大」。</li>
 * </ul>
 * 采样率（{@code management.tracing.sampling.probability}，当前 0.1）只决定 span 要不要
 * <b>上报</b>，代理层的开销是<b>每次调用都有</b>的，两者不要混淆。
 *
 * <h3>包一层会不会弄丢 HikariCP 的连接池指标</h3>
 * 不会，但这一点是<b>验证过</b>而不是假设的：{@code ProxyDataSource} 实现了
 * {@code java.sql.Wrapper} 的 {@code unwrap}/{@code isWrapperFor}，而 Boot 的
 * {@code DataSourceUnwrapper} 正是走这条路去找 {@code HikariDataSource} 的。
 * 这个指标不能丢 —— 本次诊断正是靠 {@code hikaricp_connections_timeout_total} 为 0
 * 排除了「数据库连接池是瓶颈」这个方向。部署后要复查它还在。
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@ConditionalOnClass({ DataSource.class, DataSourceObservationListener.class, ProxyDataSourceBuilder.class })
@ConditionalOnProperty(name = "mall.observability.jdbc.enabled", matchIfMissing = true)
public class JdbcObservationAutoConfiguration {

    /** 见类注释「handler 的 @Order 不是可选的」。 */
    private static final int HANDLER_ORDER = Ordered.LOWEST_PRECEDENCE - 2000;

    private static final Logger log = LoggerFactory.getLogger(JdbcObservationAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    DataSourceObservationListener mallDataSourceObservationListener(ObjectProvider<ObservationRegistry> registry) {
        // 用 Supplier 而不是直接传实例：ObservationRegistry 的解析要晚于 DataSource 的创建，
        // 提前 getObject() 会把 observation 相关的 bean 拽进过早的初始化顺序里。
        return new DataSourceObservationListener(registry::getObject);
    }

    /**
     * 把所有 DataSource bean 换成带 observation 监听的代理。
     * <p>
     * 用 BeanPostProcessor 而不是自己定义一个 DataSource bean：后者要么和 Boot 的
     * DataSourceAutoConfiguration 打架，要么得复制它那一整套属性绑定逻辑。
     * <p>
     * 方法声明成 {@code static} 是 BeanPostProcessor 的常规要求 —— 非 static 的话
     * 这个配置类本身会被过早实例化，连带把它依赖的东西一起提前初始化，
     * 常见后果是各种 {@code @ConditionalOn*} 判断在还没准备好的时候被求值。
     */
    @Bean
    static BeanPostProcessor mallDataSourceObservationPostProcessor(
            ObjectProvider<DataSourceObservationListener> listenerProvider) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (!(bean instanceof DataSource dataSource) || bean instanceof ProxyDataSource) {
                    return bean;
                }
                DataSourceObservationListener listener = listenerProvider.getObject();
                log.info("JDBC 链路埋点: 已为 DataSource [{}] 包上 observation 代理", beanName);
                return ProxyDataSourceBuilder.create(beanName, dataSource)
                        // 两个都要注册：listener 负责 SQL 执行的 span，
                        // methodListener 负责「取连接」的 span —— 后者才是连接池耗尽时
                        // 唯一能看出问题的地方，只注册前者会漏掉最想知道的那一段。
                        .listener(listener)
                        .methodListener(listener)
                        .build();
            }
        };
    }

    @Bean
    @Order(HANDLER_ORDER)
    @ConditionalOnBean(Tracer.class)
    QueryTracingObservationHandler mallQueryTracingObservationHandler(Tracer tracer) {
        return new QueryTracingObservationHandler(tracer);
    }

    @Bean
    @Order(HANDLER_ORDER)
    @ConditionalOnBean(Tracer.class)
    ConnectionTracingObservationHandler mallConnectionTracingObservationHandler(Tracer tracer) {
        return new ConnectionTracingObservationHandler(tracer);
    }

    @Bean
    @Order(HANDLER_ORDER)
    @ConditionalOnBean(Tracer.class)
    ResultSetTracingObservationHandler mallResultSetTracingObservationHandler(Tracer tracer) {
        return new ResultSetTracingObservationHandler(tracer);
    }

    /**
     * HikariCP 会在内部对同一个物理连接做一些自己的调用，不过滤的话这些会变成
     * 一堆没有意义的 span，把真正的查询埋掉。这个 filter 是库自带的，专门处理这件事。
     */
    @Bean
    @ConditionalOnMissingBean
    HikariJdbcObservationFilter mallHikariJdbcObservationFilter() {
        return new HikariJdbcObservationFilter();
    }
}

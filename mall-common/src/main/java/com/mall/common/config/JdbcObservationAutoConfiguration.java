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
 * 定位只能退回到「把每个依赖单独量一遍」，而那正是有了链路追踪本该不必再做的手工活。
 * <p>
 * 更糟的是<b>手工测量得出的结论是错的</b>：当时我看到 mall-member 响应 1–52ms、
 * HikariCP 零超时，就断定「时间全在自己的 CPU 排队上」。加上这个埋点之后才看到，
 * 光是「拿数据库连接」每次就要 100ms、一个请求里有两三次（详见下面的链路分解）。
 * 逐个依赖单独量，量不到「同时发生时的相互影响」。
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
 * <h3>实测代价：在当前测量精度下【测不出来】</h3>
 * 2026-08-27 做过 A/B：mall-coupon 限 500m CPU，每组先跑 3 轮 30 rps 预热，再压 30 秒 50 rps。
 * <pre>
 *   A1 埋点开 : 抢中 1260, 闸门拒绝 155, p95 664ms
 *   B  埋点关 : 抢中 1501, 闸门拒绝   0, p95 801ms
 *   A2 埋点开 : 抢中 1501, 闸门拒绝   0, p95  91ms
 * </pre>
 * <b>同一配置两轮之间的差异（A1 的 664ms 对 A2 的 91ms）远大于配置之间的差异</b>，
 * 主要变量是 JVM 的预热程度。所以「埋点让吞吐降了 16%」这个从 A1/B 单轮对比得出的
 * 结论是<b>错的</b>，那只是噪声。A2 的 91ms 接近无埋点时的原始基线 76ms，
 * 说明充分预热后开销很小。
 * <p>
 * 这里刻意不写一个具体的开销百分比 —— 没有测出来就不该写。要得到可信数字需要
 * 更严格的预热协议和更多重复次数。
 * <p>
 * 通用教训：<b>在 JIT 主导的系统上，单轮 A/B 对比毫无价值</b>，
 * 因为预热差异带来的波动能轻易达到一个数量级。
 *
 * <h3>它换来了什么（这才是保留它的理由）</h3>
 * 开启之后，一条 828ms 的抢购链路第一次能看到分解：
 * <pre>
 *   mall-coupon  +  0ms  828.8ms  http post /coupon/seckill/grab/{relationId}
 *   mall-coupon  +294ms  103.2ms  connection      &lt;- 拿数据库连接
 *   mall-coupon  +294ms  102.0ms  query
 *   mall-coupon  +397ms   98.7ms  HTTP GET        &lt;- Feign 客户端侧
 *   mall-member  +399ms    4.3ms  http get /member/memberreceiveaddress/...
 *   mall-coupon  +497ms  101.3ms  connection      &lt;- 又拿一次
 *   mall-coupon  +499ms   99.8ms  query
 * </pre>
 * 它立刻推翻了一个此前的错误结论：我曾根据
 * {@code hikaricp_connections_timeout_total} 恒为 0 断定「连接池不是瓶颈」——
 * 实际上<b>每次拿连接要 100ms，一个请求里有两到三次</b>。零超时只说明没超过
 * 30 秒的超时阈值，不代表没有争抢。这 200ms+ 在有链路分解之前完全看不见。
 * <p>
 * 另外 Feign 客户端侧 98.7ms 而服务端只有 4.3ms，那 94ms 的差额（连接池/负载均衡/
 * 序列化或排队）同样是之前看不到的。
 *
 * <h3>还缺 Redis</h3>
 * 上面那条链路的前 294ms 没有任何子 span —— Redis 的 Lua 调用在这一段里。
 * {@code LettuceObservationAutoConfiguration}（Boot 自带）的条件看起来都满足，
 * 但实际没有产出 span，原因还没查清。这是下一个要补的缺口。
 *
 * <h3>包一层会不会弄丢 HikariCP 的连接池指标</h3>
 * <b>不会，已在集群里实测确认</b>（Prometheus 里 {@code hikaricp_connections_max} 仍有
 * 6 个系列）。机制是 {@code ProxyDataSource} 实现了 {@code java.sql.Wrapper} 的
 * {@code unwrap}/{@code isWrapperFor}，而 Boot 的 {@code DataSourceUnwrapper} 正是走
 * 这条路去找 {@code HikariDataSource}。
 * <p>
 * 这一条有单元测试守着（{@code JdbcObservationAutoConfigurationTest}），而那条测试的
 * 第一版是个<b>假警报</b>：我按两参的 {@code unwrap(ds, HikariDataSource.class)} 断言，
 * 它返回 null，看着像代理挡住了解包。反编译 Boot 的 {@code HikariDataSourceMeterBinder}
 * 才看清它走的是三参重载 {@code unwrap(ds, HikariConfigMXBean.class, HikariDataSource.class)}。
 * 断言要对着被测系统<b>真正执行的调用</b>，对着形似的 API 断言，通过和失败都说明不了问题。
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

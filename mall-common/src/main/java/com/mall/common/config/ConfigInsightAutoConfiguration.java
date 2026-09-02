package com.mall.common.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.List;
import java.util.Locale;

/**
 * 把「这个 JVM 里某个配置的最终值是多少、来自哪一层」变成<b>指标</b>和<b>启动日志</b>。
 *
 * <h3>要解决的问题</h3>
 * 本项目有四层配置（Config Server / K8s 环境变量 / mall-common-default.properties /
 * 服务自己的 application.yml），同名 key 的优先级<b>不能靠推断</b>。
 * 2026-09-02 就栽过一次：{@code TRACING_SAMPLE_RATE} 环境变量被 Config Server 里
 * 硬编码的同名属性盖掉，采样率一直是 1.0 而不是以为的 0.1，
 * 「压测把 Tempo 打成 OOMKilled 已经修好了」这个结论是假的 —— 而这个错误潜伏了很久，
 * 直到开放 {@code /actuator/env} 才被发现。
 *
 * <h3>为什么做成指标，而不是一个页面或一个脚本</h3>
 * <ul>
 *   <li><b>按实例天然分开</b>：每个 pod 一条独立序列。同一服务两个副本配置不一致
 *       ——请求打到哪个 pod 行为不同，这类问题极难查 —— 在指标模型里是一条 PromQL
 *       就能发现的事。页面要费劲处理的多实例问题，这里是免费的。</li>
 *   <li><b>能告警</b>：mall-deploy 的告警规则里有「同一服务的副本配置不一致」，
 *       把一次性的人工检查变成了持续的不变量。这是 dyn/admin 和 Spring Boot Admin
 *       都给不了的。</li>
 *   <li><b>有历史</b>：能看出某个值什么时候变的、跟哪次发布对得上。</li>
 *   <li><b>零新增基础设施</b>：Prometheus 和 Grafana 已经在跑，不用新服务、不用新鉴权面。</li>
 * </ul>
 * 形态照抄 {@code jvm_info}：值恒为 1，信息全在标签里。
 *
 * <h3>为什么必须是精选清单，而不是全量</h3>
 * 两个原因，都是硬约束：
 * <ol>
 *   <li><b>标签基数</b>。全量是 800+ 个属性 × 25 个 pod，会把 Prometheus 的序列数
 *       撑起来，而这个集群的 TSDB 只有 2Gi。</li>
 *   <li><b>密钥</b>。见下面的 {@link #isSensitive}。</li>
 * </ol>
 *
 * <h3>安全：绝不能把密钥变成标签</h3>
 * 指标会被抓取、存 3 天、任何能打开 Grafana 的人都看得到。
 * 白名单本身是第一道防线，但白名单是人维护的 —— 将来有人顺手加一条
 * {@code spring.datasource.password}，就等于把数据库密码发布出去。
 * 所以这里还有第二道：{@link #isSensitive} 按属性名拦截，命中就跳过并打 warn。
 * {@code ConfigInsightTest} 会验证这道拦截真的拦得住。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mall.config-insight.enabled", havingValue = "true", matchIfMissing = true)
public class ConfigInsightAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ConfigInsightAutoConfiguration.class);

    /** 指标名。照 {@code jvm_info} 的形态：值恒为 1，信息在标签里。 */
    static final String METRIC_NAME = "mall.config.info";

    /**
     * 精选清单：只放「改了会影响运行时行为、但不看就不知道改没改」的项。
     * <p>
     * 挑选标准是「这个值错了会怎样」，不是「这个值有没有意思」：
     * 每一条都对应过一次真实的排查或事故。
     */
    static final List<String> WATCHED = List.of(
            // 采样率 —— 就是它坑过人。改错了压测会把 Tempo 打成 OOMKilled。
            "management.tracing.sampling.probability",
            // 连接池 —— MySQL max_connections=151，6 个连库服务 × 池大小，滚更时翻倍。
            // 这三个值配错了表现是「过载时延迟反而更差」，很难归因。
            "spring.datasource.hikari.maximum-pool-size",
            "spring.datasource.hikari.minimum-idle",
            "spring.datasource.hikari.connection-timeout",
            // Feign 超时 —— 熔断的慢调用阈值(2s)是刻意设在 read-timeout 之下的，
            // 这两个值一旦失衡，熔断就会在超时堆积之后才反应，等于失效。
            "spring.cloud.openfeign.client.config.default.connect-timeout",
            "spring.cloud.openfeign.client.config.default.read-timeout",
            // 熔断参数
            "resilience4j.circuitbreaker.configs.default.failure-rate-threshold",
            "resilience4j.circuitbreaker.configs.default.slow-call-duration-threshold",
            "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool",
            // actuator 暴露面 —— 网关必须比后端窄，写错了是安全问题
            "management.endpoints.web.exposure.include",
            // 基础设施指向：连错环境是最尴尬的一类故障
            "spring.cloud.consul.host",
            "spring.config.import");

    /**
     * 属性名里出现这些片段就拒绝发布，无论它是否在白名单里。
     * <p>
     * 这是给「将来某个人顺手往 WATCHED 里加一行」准备的 —— 白名单是人维护的，
     * 而把密码变成 Prometheus 标签是不可撤销的（已经抓走的样本删不掉）。
     */
    static final List<String> SENSITIVE_FRAGMENTS = List.of(
            "password", "passwd", "secret", "credential", "token", "private-key", "privatekey");

    /** 标签值上限。超长的值（比如一整串 sentinel 地址）截断，避免撑大序列。 */
    static final int MAX_VALUE_LENGTH = 120;

    static boolean isSensitive(String propertyName) {
        if (propertyName == null) {
            return false;
        }
        String lower = propertyName.toLowerCase(Locale.ROOT);
        for (String fragment : SENSITIVE_FRAGMENTS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    static String truncate(String value) {
        if (value == null) {
            return "<unset>";
        }
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH) + "…";
    }

    /**
     * 找出这个属性最终来自哪个 PropertySource —— 也就是「谁赢了」。
     * <p>
     * 做法和 {@code /actuator/env} 一致：按优先级顺序遍历，第一个含有这个 key 的就是赢家。
     * 注意这里返回的是<b>定义了这个 key 的源</b>，占位符（如 {@code ${TRACING_SAMPLE_RATE:1.0}}）
     * 的实际取值可能来自别处 —— 这正是那次踩坑的形状，所以值和来源要分开看。
     */
    static String resolveSource(ConfigurableEnvironment environment, String propertyName) {
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable
                    && enumerable.containsProperty(propertyName)) {
                return source.getName();
            }
        }
        return "<unknown>";
    }

    @Bean
    MeterBinder mallConfigInfoMetrics(ConfigurableEnvironment environment) {
        return registry -> {
            for (String property : WATCHED) {
                if (isSensitive(property)) {
                    log.warn("配置洞察：属性 {} 命中敏感词拦截，不发布为指标。"
                            + "把密钥放进 WATCHED 是错的 —— 指标会被抓取并保留数日。", property);
                    continue;
                }
                String value = environment.getProperty(property);
                Gauge.builder(METRIC_NAME, () -> 1.0d)
                        .description("这个实例上某个配置项的最终值及其来源（值恒为 1，信息在标签里）")
                        .tag("property", property)
                        .tag("value", truncate(value))
                        .tag("source", value == null ? "<unset>" : resolveSource(environment, property))
                        .register(registry);
            }
        };
    }

    /**
     * 启动时把同一批配置打进日志。
     * <p>
     * 和指标是互补而不是重复：指标回答「现在是什么」并且能告警，
     * 日志回答「这个 pod 起来的那一刻加载了什么」—— 后者在事后复盘时更可靠，
     * 因为 pod 早就没了，指标也随之消失，而日志还在 Loki 里。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logEffectiveConfig(ApplicationReadyEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        for (String property : WATCHED) {
            if (isSensitive(property)) {
                continue;
            }
            String value = environment.getProperty(property);
            log.info("配置洞察: {} = {} (来自 {})",
                    property, truncate(value), value == null ? "<unset>" : resolveSource(environment, property));
        }
    }
}

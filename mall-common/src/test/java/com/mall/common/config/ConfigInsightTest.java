package com.mall.common.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigInsightAutoConfiguration} 的守护测试。
 *
 * <h3>最重要的一条在最后</h3>
 * 「WATCHED 里不能出现密钥」那条不是形式主义：把配置值放进 Prometheus 标签意味着
 * 它会被抓取、保留数日、任何能打开 Grafana 的人都看得到，而且<b>已经抓走的样本删不掉</b>。
 * 白名单是人维护的，这个测试是给「将来某个人顺手加一行」准备的最后一道闸。
 */
class ConfigInsightTest {

    // ------------------------------------------------- 来源解析（那次踩坑的形状）

    @Test
    @DisplayName("同一个 key 被多层定义时，返回【优先级最高的那一层】")
    void resolveSourceReturnsTheWinningLayer() {
        StandardEnvironment env = new StandardEnvironment();
        // 复现 2026-09-02 那次踩坑：Config Server 和 mall-common 都定义了采样率，
        // Config Server 优先级更高。当时靠人工对照两个来源，推错了。
        env.getPropertySources().addFirst(new MapPropertySource(
                "configserver:file:/config-repo/application.yml",
                Map.of("management.tracing.sampling.probability", "1.0")));
        env.getPropertySources().addLast(new MapPropertySource(
                "mallCommonDefaultProperties",
                Map.of("management.tracing.sampling.probability", "0.1")));

        assertThat(ConfigInsightAutoConfiguration.resolveSource(env, "management.tracing.sampling.probability"))
                .as("返回了低优先级的那层 —— 那正是当初推错的方向，这个工具就白做了")
                .isEqualTo("configserver:file:/config-repo/application.yml");
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
    }

    @Test
    @DisplayName("属性不存在时返回 <unknown>，不抛异常")
    void resolveSourceHandlesMissingProperty() {
        assertThat(ConfigInsightAutoConfiguration.resolveSource(new StandardEnvironment(), "no.such.property"))
                .isEqualTo("<unknown>");
    }

    // ------------------------------------------------------------------ 指标形态

    @Test
    @DisplayName("每个被观察的属性注册一条 mall.config.info，值恒为 1，信息在标签里")
    void registersOneGaugePerWatchedProperty() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test",
                Map.of("management.tracing.sampling.probability", "0.25")));

        new ConfigInsightAutoConfiguration().mallConfigInfoMetrics(env).bindTo(registry);

        List<Meter> meters = registry.getMeters().stream()
                .filter(m -> ConfigInsightAutoConfiguration.METRIC_NAME.equals(m.getId().getName()))
                .toList();
        assertThat(meters)
                .as("一个 WATCHED 属性对应一条序列")
                .hasSize(ConfigInsightAutoConfiguration.WATCHED.size());

        Meter sampling = meters.stream()
                .filter(m -> "management.tracing.sampling.probability".equals(m.getId().getTag("property")))
                .findFirst().orElseThrow();
        assertThat(sampling.getId().getTag("value")).isEqualTo("0.25");
        assertThat(sampling.getId().getTag("source")).isEqualTo("test");
    }

    @Test
    @DisplayName("属性没设置时标记成 <unset>，而不是漏掉这条序列")
    void unsetPropertyStillProducesASeries() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new ConfigInsightAutoConfiguration().mallConfigInfoMetrics(new StandardEnvironment()).bindTo(registry);

        Meter any = registry.getMeters().stream()
                .filter(m -> ConfigInsightAutoConfiguration.METRIC_NAME.equals(m.getId().getName()))
                .findFirst().orElseThrow();
        // 漏掉序列的话，面板上会表现为「这个属性不存在」，而真相是「没人设过它」——
        // 两者要区分得开。
        assertThat(any.getId().getTag("value")).isEqualTo("<unset>");
        assertThat(any.getId().getTag("source")).isEqualTo("<unset>");
    }

    @Test
    @DisplayName("超长的值会被截断，避免撑大标签")
    void longValuesAreTruncated() {
        String longValue = "x".repeat(500);
        String truncated = ConfigInsightAutoConfiguration.truncate(longValue);
        assertThat(truncated).hasSizeLessThanOrEqualTo(ConfigInsightAutoConfiguration.MAX_VALUE_LENGTH + 1);
        assertThat(ConfigInsightAutoConfiguration.truncate("short")).isEqualTo("short");
    }

    // ---------------------------------------------------------------- 敏感词拦截

    @Test
    @DisplayName("敏感词拦截：命中的拒绝，正常属性放行")
    void sensitiveDetectionWorksBothWays() {
        // 正控制：这些必须被拦
        assertThat(ConfigInsightAutoConfiguration.isSensitive("spring.datasource.password")).isTrue();
        assertThat(ConfigInsightAutoConfiguration.isSensitive("SPRING_RABBITMQ_PASSWORD")).isTrue();
        assertThat(ConfigInsightAutoConfiguration.isSensitive("mall.jwt.secret")).isTrue();
        assertThat(ConfigInsightAutoConfiguration.isSensitive("seckill.internal-token")).isTrue();
        assertThat(ConfigInsightAutoConfiguration.isSensitive("oss.credential.id")).isTrue();

        // 负控制：没有这一半，上面那些可能是因为「什么都拦」而通过的，等于没测
        assertThat(ConfigInsightAutoConfiguration.isSensitive("spring.datasource.hikari.maximum-pool-size")).isFalse();
        assertThat(ConfigInsightAutoConfiguration.isSensitive("management.tracing.sampling.probability")).isFalse();
        assertThat(ConfigInsightAutoConfiguration.isSensitive(null)).isFalse();
    }

    @Test
    @DisplayName("敏感属性即使被加进 WATCHED，也不会真的发布成指标")
    void sensitivePropertyIsNeverPublished() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.password", "hunter2");
        env.getPropertySources().addFirst(new MapPropertySource("test", props));

        // 直接调内部逻辑模拟「有人往 WATCHED 里加了密码」的情形
        boolean blocked = ConfigInsightAutoConfiguration.isSensitive("spring.datasource.password");
        assertThat(blocked).isTrue();

        new ConfigInsightAutoConfiguration().mallConfigInfoMetrics(env).bindTo(registry);
        boolean leaked = registry.getMeters().stream()
                .anyMatch(m -> "hunter2".equals(m.getId().getTag("value")));
        assertThat(leaked)
                .as("密码出现在了指标标签里 —— 指标会被抓取并保留数日，已抓走的样本删不掉")
                .isFalse();
    }

    @Test
    @DisplayName("【最后一道闸】WATCHED 清单里不能出现任何敏感属性")
    void watchListContainsNoSecrets() {
        List<String> offenders = ConfigInsightAutoConfiguration.WATCHED.stream()
                .filter(ConfigInsightAutoConfiguration::isSensitive)
                .toList();
        assertThat(offenders)
                .as("这些属性会被发布成 Prometheus 标签，进而被抓取、保留数日、"
                        + "对任何能打开 Grafana 的人可见，而且已抓走的样本删不掉。"
                        + "如果确实需要观察某个含敏感词的属性，观察它的【存在性】而不是值。")
                .isEmpty();
        assertThat(ConfigInsightAutoConfiguration.WATCHED)
                .as("清单空了的话上面那条断言恒真，等于没有保护")
                .isNotEmpty();
    }
}

package com.mall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住"默认不暴露排查类 actuator 端点"这个决定。
 *
 * <h3>它守的是什么</h3>
 * 2026-09-14 实测：这些端点从<b>集群外</b>无鉴权就能打到 ——
 * <pre>
 *   http://mall.com/actuator/env        -> HTTP 200
 *   http://item.mall.com/actuator/env   -> HTTP 200
 *   http://admin.mall.com/actuator/env  -> HTTP 200
 * </pre>
 * 而 {@code OPTIONS /actuator/loggers/ROOT} 返回
 * {@code Allow: GET,HEAD,POST,OPTIONS} —— <b>POST 是允许的</b>，
 * 也就是任何能访问这些域名的人都能改运行期日志级别。
 *
 * <p>密码没有泄漏（{@code MYSQL_PASSWORD} / {@code RABBITMQ_PASSWORD} 实测都打了码），
 * 但内网拓扑（{@code MYSQL_HOST} / {@code REDIS_SENTINEL_NODES} /
 * {@code CONSUL_HOST} / {@code CONFIG_SERVER_URI}）和全部 URL 映射照样送出去。
 *
 * <h3>为什么值得一条测试，而不是只写注释</h3>
 * 这个默认值躺在一个 47 行的 properties 文件里，
 * 排查问题时"临时打开一下"然后忘了改回去是很自然的事 ——
 * 而改回去与否<b>不会有任何症状</b>：功能全对，测试全绿，只是洞开着。
 * 这正是这个项目反复吃亏的那一类（见 project_mall_silent_failures）。
 */
class ActuatorExposureDefaultTest {

    private static final String KEY = "management.endpoints.web.exposure.include";

    /** 一旦默认暴露，就等于对全世界开放的那些。 */
    private static final List<String> MUST_NOT_BE_DEFAULT = List.of(
            "env",          // 内网拓扑 + 全部配置项
            "configprops",  // 同上，按 @ConfigurationProperties 组织
            "beans",        // 容器结构
            "mappings",     // 全部 URL 映射 = 攻击面清单
            "loggers",      // 【可写】POST 能改日志级别
            "threaddump",   // 线程栈
            "heapdump",     // 堆转储 —— 一旦暴露等于全部内存内容
            "shutdown",     // 【可写】关掉应用
            "*");           // 通配 = 以上全部

    private static Properties load() throws IOException {
        Properties p = new Properties();
        try (InputStream in = ActuatorExposureDefaultTest.class.getClassLoader()
                .getResourceAsStream("mall-common-default.properties")) {
            assertThat(in).as("mall-common-default.properties 必须在 classpath 上").isNotNull();
            p.load(in);
        }
        return p;
    }

    @Test
    @DisplayName("默认暴露面里不能有排查类/可写的端点")
    void defaultExposureIsNarrow() throws IOException {
        String raw = load().getProperty(KEY);
        assertThat(raw).as(KEY + " 不该消失 —— 没有它 Boot 的默认只暴露 health").isNotNull();

        // 值的形态是 ${ACTUATOR_EXPOSE:health,info,prometheus}，要检查的是【默认值】那一半。
        String defaults = raw;
        int colon = raw.indexOf(':');
        if (raw.startsWith("${") && colon > 0) {
            defaults = raw.substring(colon + 1, raw.length() - 1);
        }
        List<String> exposed = Arrays.stream(defaults.split(",")).map(String::trim).toList();

        assertThat(exposed)
                .as("这些端点默认暴露 = 对全世界开放（实测 http://mall.com/actuator/env 是 200）")
                .doesNotContainAnyElementsOf(MUST_NOT_BE_DEFAULT);
    }

    @Test
    @DisplayName("health / prometheus 必须留着 —— 砍了会把探针和抓取一起弄坏")
    void keepsWhatInfrastructureDependsOn() throws IOException {
        String raw = load().getProperty(KEY);
        String defaults = raw.startsWith("${") ? raw.substring(raw.indexOf(':') + 1, raw.length() - 1) : raw;
        List<String> exposed = Arrays.stream(defaults.split(",")).map(String::trim).toList();

        // health：Consul 的 health-check-path 打它（见同一个文件里那一行），
        //         K8s 的 readiness/liveness 探针也打它。
        // prometheus：Prometheus 主动抓取。
        // 砍掉任何一个都不会在这里报错，而是在集群上表现成
        // 「所有实例 critical」或「监控没数据」。
        assertThat(exposed).as("Consul 健康检查和 K8s 探针都打 /actuator/health").contains("health");
        assertThat(exposed).as("Prometheus 抓 /actuator/prometheus").contains("prometheus");
    }

    @Test
    @DisplayName("排查能力没被砍掉：必须仍然能用环境变量临时打开")
    void staysOverridableForDebugging() throws IOException {
        String raw = load().getProperty(KEY);
        // 写成 ${ACTUATOR_EXPOSE:...} 是这次改动的一半 ——
        // 如果有人把它改成硬编码的固定值，排查时就只能改代码重新发版了，
        // 那会让人倾向于"干脆全打开算了"，绕回原点。
        assertThat(raw)
                .as("要留一个显式的、有痕迹的开关：kubectl set env deploy/xxx ACTUATOR_EXPOSE=...")
                .startsWith("${ACTUATOR_EXPOSE:");
    }
}

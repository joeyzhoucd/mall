package com.mall.common.config;

import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 拿 classpath 上所有依赖自带的 spring-configuration-metadata.json 去校验本模块的配置文件。
 *
 * <h3>为什么需要它</h3>
 * Boot 2.7 到 Boot 4 这次迁移里，最难查的一类问题不是编译错误、也不是启动崩溃，而是
 * 【配置项静默失效】：属性名变了，写旧名字不报错、不告警，只是那段配置完全不生效。
 * 实际踩到的（每一个都是 pod 全绿、健康检查通过、功能坏掉）：
 * <ul>
 *   <li>spring.redis.* 改成了 spring.data.redis.*：7 个服务的 Redis 地址全没生效，
 *       Boot 回落到 localhost:6379，Session、缓存、网关限流全废。</li>
 *   <li>spring.cloud.gateway.routes.* 改成了 spring.cloud.gateway.server.webflux.routes.*：
 *       网关一条路由都没加载，所有 /api 请求 404，整站前端等于挂了。</li>
 *   <li>server.servlet.encoding.* 改成了 spring.servlet.encoding.*：中文表单变乱码。</li>
 *   <li>management.otlp.tracing.endpoint 改成了
 *       management.opentelemetry.tracing.export.otlp.endpoint：链路追踪一条 span 都没上报。</li>
 *   <li>feign.* 改成了 spring.cloud.openfeign.*：超时和连接池配置全部回落默认值。</li>
 * </ul>
 * 这些当初是靠手工把所有依赖 jar 的元数据抽出来做交集才找到的。这个类把那个过程固化下来，
 * 让它变成一条能在 CI 里跑的断言，而不是一次性的排查手法。
 *
 * <h3>两个检查维度，缺一不可</h3>
 * <ol>
 *   <li><b>error 级废弃</b>：元数据里有这个名字，但标了 deprecation.level=error。
 *       这类名字保留在元数据里只是为了让工具能提示你它没了，绑定层面已经不认。</li>
 *   <li><b>元数据里根本不存在</b>：属性被重命名却没留 deprecation 记录（网关路由就是这种），
 *       或者纯粹拼错。只查第一个维度会完整漏掉这一类，而本次迁移最严重的两个问题
 *       （网关路由、链路追踪）都只有第二个维度能发现。</li>
 * </ol>
 *
 * <h3>实现上的两个刻意选择</h3>
 * 用 Spring 自己的 {@link YamlPropertySourceLoader} 而不是手写 YAML 展平：它产出的属性名
 * 和 Spring 真正绑定时用的完全一致（正确处理列表下标、多文档、松散绑定）。手写展平器会把
 * routes[0].id 这种列表项误报成未知属性，这个坑在排查阶段实际遇到过。
 * <p>
 * 只校验框架自己的命名空间（见 FRAMEWORK_NAMESPACES）。业务自定义的前缀（mall.*、pay.*、
 * elasticsearch.* 这些走 &#64;Value 或自定义 &#64;ConfigurationProperties 的）不在任何依赖的
 * 元数据里，拿它们比对只会产生噪声。
 */
public final class ConfigMetadataChecker {

    /**
     * 参与校验的命名空间。只放"由第三方依赖定义、且会生成元数据"的前缀，
     * 业务自己的前缀不要加进来。
     */
    private static final List<String> FRAMEWORK_NAMESPACES = List.of(
            "spring.", "management.", "server.", "logging.", "mybatis-plus.", "springdoc."
    );

    /** 属性名里的列表下标，比对前统一去掉：routes[0].id 变成 routes.id */
    private static final Pattern INDEX = Pattern.compile("\\[\\d+]");

    private ConfigMetadataChecker() {
    }

    /** 一条问题记录。kind 取 DEPRECATED_ERROR 或 UNKNOWN。 */
    public record Problem(String source, String key, String kind, String detail) {
        @Override
        public String toString() {
            return "  [" + kind + "] " + source + " -> " + key
                    + (detail == null || detail.isBlank() ? "" : ("   " + detail));
        }
    }

    /**
     * 校验给定的 classpath 配置文件。
     *
     * @param classpathConfigs 形如 application.yml、mall-common-default.properties
     * @return 发现的问题；为空表示通过
     */
    public static List<Problem> check(String... classpathConfigs) {
        Metadata meta = loadMetadata();
        List<Problem> problems = new ArrayList<>();
        for (String cfg : classpathConfigs) {
            for (String key : loadKeys(cfg)) {
                if (!isFrameworkNamespace(key)) {
                    continue;
                }
                String normalized = INDEX.matcher(key).replaceAll("");
                String replacement = meta.deprecatedError().get(normalized);
                if (replacement != null) {
                    problems.add(new Problem(cfg, key, "DEPRECATED_ERROR", "应改用 -> " + replacement));
                    continue;
                }
                if (!meta.isKnown(normalized)) {
                    problems.add(new Problem(cfg, key, "UNKNOWN", "元数据里没有这个属性（可能已被重命名或拼错）"));
                }
            }
        }
        return problems;
    }

    private static boolean isFrameworkNamespace(String key) {
        for (String ns : FRAMEWORK_NAMESPACES) {
            if (key.startsWith(ns)) {
                return true;
            }
        }
        return false;
    }

    /** 读取一个 classpath 配置文件里所有有值的叶子属性名。 */
    private static Set<String> loadKeys(String classpathConfig) {
        Resource resource = new ClassPathResource(classpathConfig);
        if (!resource.exists()) {
            throw new IllegalArgumentException("classpath 上找不到配置文件: " + classpathConfig);
        }
        try {
            List<PropertySource<?>> sources = classpathConfig.endsWith(".properties")
                    ? new PropertiesPropertySourceLoader().load(classpathConfig, resource)
                    : new YamlPropertySourceLoader().load(classpathConfig, resource);
            Set<String> keys = new HashSet<>();
            for (PropertySource<?> source : sources) {
                if (source instanceof EnumerablePropertySource<?> enumerable) {
                    keys.addAll(List.of(enumerable.getPropertyNames()));
                }
            }
            return keys;
        } catch (IOException ex) {
            throw new IllegalStateException("读取配置文件失败: " + classpathConfig, ex);
        }
    }

    /** classpath 上所有依赖贡献的属性名、组前缀，以及 error 级废弃映射。 */
    private record Metadata(Set<String> known, Map<String, String> deprecatedError) {

        /**
         * 判断一个属性名是否已知。除了精确匹配，还要接受"某个已知名字是它的前缀"：
         * Map 型配置（比如 spring.cloud.openfeign.client.config.&lt;name&gt;.connect-timeout）
         * 元数据里只有到 Map 那一层的名字，后面的键是使用方自定义的。
         */
        boolean isKnown(String key) {
            if (known.contains(key)) {
                return true;
            }
            for (int i = key.lastIndexOf('.'); i > 0; i = key.lastIndexOf('.', i - 1)) {
                if (known.contains(key.substring(0, i))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static Metadata loadMetadata() {
        Set<String> known = new HashSet<>();
        Map<String, String> deprecatedError = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:META-INF/spring-configuration-metadata.json");
            for (Resource resource : resources) {
                try (InputStream in = resource.getInputStream()) {
                    JsonNode root = mapper.readTree(in);
                    collect(root.path("groups"), known, null);
                    collect(root.path("properties"), known, deprecatedError);
                } catch (RuntimeException | IOException ignored) {
                    // 单个依赖的元数据格式异常不该让整个校验失败
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("扫描 spring-configuration-metadata.json 失败", ex);
        }
        if (known.isEmpty()) {
            // 这一条很重要：排查阶段有过好几次"扫描工具自己静默失效、于是得出零问题的假结论"。
            // 扫不到任何元数据说明校验器本身没工作，必须当成失败，不能当成通过。
            throw new IllegalStateException(
                    "classpath 上没有扫到任何 spring-configuration-metadata.json，"
                    + "说明校验器本身失效了，不能把这种情况当成校验通过。");
        }
        return new Metadata(known, deprecatedError);
    }

    private static void collect(JsonNode array, Set<String> known, Map<String, String> deprecatedError) {
        if (!array.isArray()) {
            return;
        }
        for (JsonNode node : array) {
            String name = node.path("name").asString("");
            if (name.isBlank()) {
                continue;
            }
            known.add(name);
            if (deprecatedError == null) {
                continue;
            }
            JsonNode deprecation = node.path("deprecation");
            if (deprecation.isObject() && "error".equals(deprecation.path("level").asString(""))) {
                deprecatedError.put(name, deprecation.path("replacement").asString("（无替代属性）"));
            }
        }
    }
}

package com.mall.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 mall-common 里<b>每一个</b>自动配置类的一条硬约束：
 * <b>外层类的方法签名不得出现 optional 依赖的类型。</b>
 *
 * <h3>这条规则是花了一次线上故障换来的</h3>
 * 2026-08-27，{@link EagerConnectionWarmup} 把三个 {@code @Bean} 都放在外层类、
 * 只在方法上挂 {@code @ConditionalOnClass}，结果 <b>9 个服务全部 CrashLoopBackOff</b>：
 * <pre>
 * Failed to introspect Class [com.mall.common.config.EagerConnectionWarmup]
 * Caused by: NoClassDefFoundError: org/springframework/amqp/rabbit/connection/ConnectionFactory
 *   at java.lang.Class.getDeclaredMethods0(Native Method)
 *   at org.springframework.util.ReflectionUtils.getDeclaredMethods(...)
 * </pre>
 * 当时的（错误的）想法是「Spring 用 ASM 读注解判定条件，不会为了判定去加载方法参数
 * 里的类型」。实际上 Spring 必须先调 {@code Class.getDeclaredMethods()} 才能找到
 * {@code @Bean} 方法，而<b>那一步会解析每个方法签名上的全部类型</b> ——
 * 方法级的条件注解还没轮到被求值。
 * <p>
 * 正确写法是把这类 {@code @Bean} 放进<b>嵌套静态类</b>，{@code @ConditionalOnClass}
 * 挂在<b>类</b>上：条件不成立时嵌套类整体不被自省。Spring Boot 自己到处是这个写法
 * （例如 {@code DataSourceConfiguration$Hikari}），它不是风格偏好，是唯一能工作的方式。
 *
 * <h3>为什么做成【覆盖全部自动配置】的通用测试</h3>
 * 这个错误对写代码的人是<b>不可见</b>的：mall-common 自己的编译和测试 classpath 上
 * 有那些 optional 依赖（optional 对声明它的模块本身可见），所以本地一切正常，
 * 只在「不引这个依赖的服务」里发作。任何一个新写的自动配置都可能再踩一次，
 * 所以规则要挂在<b>规则</b>上，而不是挂在犯过错的那一个类上。
 *
 * <h3>为什么还要顺便核对 pom 里 optional 依赖的数量</h3>
 * 下面的包名前缀是手写的清单。如果有人新加了一个 optional 依赖而忘了更新清单，
 * 这个测试会<b>继续通过</b>却不再覆盖新的那一个 —— 一个覆盖面悄悄缩小的测试
 * 比没有测试更危险，因为它给人已经守住了的错觉。所以额外断言
 * 「pom 里 optional 依赖的个数 == 清单预期的个数」，加了新的就必须回来更新这里。
 */
class AutoConfigurationSignatureTest {

    /**
     * mall-common 声明成 optional 的依赖 → 它们的包名前缀。
     * <p>
     * 新增 optional 依赖时<b>必须</b>在这里补一行，否则下面的数量断言会失败。
     */
    private static final Map<String, String> OPTIONAL_ARTIFACT_TO_PACKAGE = Map.of(
            "spring-boot-starter-amqp", "org.springframework.amqp.",
            "spring-boot-starter-data-redis", "org.springframework.data.redis.",
            // 包名前缀是 io.swagger.v3. 而不是 org.springdoc. —— @Bean 的返回类型是
            // io.swagger.v3.oas.models.OpenAPI（来自 springdoc 传递引入的 swagger-models）。
            // 要检查的是【方法签名里出现的类型】，不是依赖的 groupId。
            "springdoc-openapi-starter-webmvc-ui", "io.swagger.v3.");

    @Test
    @DisplayName("每个自动配置的外层类方法签名都不引用 optional 依赖的类型")
    void noAutoConfigurationOuterClassReferencesOptionalTypes() throws Exception {
        List<Class<?>> autoConfigurations = loadRegisteredAutoConfigurations();
        assertThat(autoConfigurations)
                .as("没有读到任何自动配置类 —— 这个测试就什么都没检查，先修它")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Class<?> type : autoConfigurations) {
            // 只看【外层类自己声明的】方法。嵌套类的方法（包括其中的 lambda）
            // 属于嵌套类，不在这里，这正是嵌套类写法能规避问题的原因。
            for (Method method : type.getDeclaredMethods()) {
                List<Class<?>> signature = new ArrayList<>(List.of(method.getParameterTypes()));
                signature.add(method.getReturnType());
                for (Class<?> sigType : signature) {
                    for (String pkg : OPTIONAL_ARTIFACT_TO_PACKAGE.values()) {
                        if (sigType.getName().startsWith(pkg)) {
                            violations.add(type.getSimpleName() + "." + method.getName()
                                    + " 的签名里有 " + sigType.getName());
                        }
                    }
                }
            }
        }

        assertThat(violations)
                .as("这些自动配置的外层类引用了 optional 依赖的类型。不引该依赖的服务会在"
                        + " Class.getDeclaredMethods() 自省时抛 NoClassDefFoundError 而整体启动失败"
                        + "（2026-08-27 实际导致 9 个服务 CrashLoopBackOff）。"
                        + " 把对应的 @Bean 移到带【类级】@ConditionalOnClass 的嵌套静态类里。")
                .isEmpty();
    }

    @Test
    @DisplayName("pom 里 optional 依赖的个数和上面的清单一致（防止测试覆盖面悄悄缩小）")
    void optionalDependencyListIsUpToDate() throws IOException {
        // surefire 以模块目录为工作目录运行，所以 pom.xml 是相对路径。
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile(
                        "<artifactId>([^<]+)</artifactId>\\s*(?:<version>[^<]*</version>\\s*)?<optional>true</optional>")
                .matcher(pom);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1));
        }

        assertThat(found)
                .as("pom.xml 里没找到任何 <optional>true</optional> 依赖 —— 要么 pom 变了，"
                        + "要么这个正则失效了。无论哪种，上面那条签名检查的前提都没了，先查这里。")
                .isNotEmpty();
        assertThat(found)
                .as("pom 里的 optional 依赖和 OPTIONAL_ARTIFACT_TO_PACKAGE 清单不一致。"
                        + " 新增 optional 依赖时要在清单里补上它的包名前缀，"
                        + "否则签名检查不会覆盖新的那一个 —— 覆盖面悄悄缩小的测试比没有测试更危险。")
                .containsExactlyInAnyOrderElementsOf(OPTIONAL_ARTIFACT_TO_PACKAGE.keySet());
    }

    /**
     * {@code @Bean} 上的 {@code @ConditionalOnBean} 必须配一个类级的 after/afterName。
     *
     * <h3>这条规则同样是花了一轮 CI 换来的（2026-09-02）</h3>
     * {@code BusinessMetricsAutoConfiguration} 一开始写成
     * {@code @Bean @ConditionalOnBean(MeterRegistry.class)}，看起来完全合理。
     * 结果 mall-order / mall-coupon / mall-ware 三个服务的 Spring 上下文<b>全都起不来</b>。
     * <p>
     * 原因：自动配置上的 {@code @ConditionalOnBean} 是在<b>该自动配置类被处理的那一刻</b>
     * 求值的，而自动配置的先后由排序决定；没有 before/after 声明时按全限定类名排序，
     * {@code com.mall.*} 排在 {@code org.springframework.*} 前面。于是条件求值时
     * Boot 的 metrics 自动配置还没跑，{@code MeterRegistry} 还不存在，bean 不创建 ——
     * 而那三个服务里是<b>必需</b>的字段注入。
     * <p>
     * 这个错误对写代码的人同样是不可见的：单元测试全绿、{@code helm template} 全绿、
     * {@code kubectl apply --dry-run} 全绿，只有真正启上下文的集成测试会挂。
     * 如果没有那一步集成测试，三个服务会在部署后才 CrashLoopBackOff。
     * <p>
     * 两种正确写法，任选其一：
     * <ul>
     *   <li>在类上声明 {@code @AutoConfiguration(after/afterName = ...)}，点名那个提供
     *       目标 bean 的自动配置 —— {@link JdbcObservationAutoConfiguration} 就是这么做的；</li>
     *   <li>干脆不用 {@code @ConditionalOnBean}：把参数改成
     *       {@code ObjectProvider<T>}，它在<b>bean 创建时</b>才解析，与排序无关。
     *       如果调用方是必需注入，这个才是唯一安全的选择，因为它能保证 bean 总是产出。</li>
     * </ul>
     */
    @Test
    @DisplayName("@Bean 上用了 @ConditionalOnBean 的自动配置必须声明 after/afterName")
    void conditionalOnBeanRequiresExplicitOrdering() throws Exception {
        List<Class<?>> autoConfigurations = loadRegisteredAutoConfigurations();
        assertThat(autoConfigurations)
                .as("没有读到任何自动配置类 —— 这个测试就什么都没检查，先修它")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        int inspected = 0;
        for (Class<?> type : autoConfigurations) {
            // 外层类 + 嵌套类都要看：@ConditionalOnBean 放在嵌套类的 @Bean 上，
            // 求值时机依然由【外层自动配置类】的排序决定。
            List<Class<?>> candidates = new ArrayList<>();
            candidates.add(type);
            candidates.addAll(List.of(type.getDeclaredClasses()));

            boolean usesConditionalOnBean = false;
            for (Class<?> candidate : candidates) {
                for (Method method : candidate.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Bean.class)
                            && method.isAnnotationPresent(ConditionalOnBean.class)) {
                        usesConditionalOnBean = true;
                    }
                }
            }
            if (!usesConditionalOnBean) {
                continue;
            }
            inspected++;

            AutoConfiguration annotation = type.getAnnotation(AutoConfiguration.class);
            boolean ordered = annotation != null
                    && (annotation.after().length > 0 || annotation.afterName().length > 0);
            if (!ordered) {
                violations.add(type.getSimpleName());
            }
        }

        // 一个都没扫到就说明检测手段失效了（注解被换、嵌套类结构变了），
        // 而"没有违规"和"什么都没检查"在结果上长得一模一样 —— 这正是要防的那种失效。
        assertThat(inspected)
                .as("没有找到任何用 @ConditionalOnBean 的 @Bean。项目里至少有一处"
                        + "（JdbcObservationAutoConfiguration 的 tracing handler）。"
                        + "扫到 0 个说明这个检查已经失效，而失效的表现和'全部合规'一样。")
                .isPositive();

        assertThat(violations)
                .as("这些自动配置在 @Bean 上用了 @ConditionalOnBean 却没声明类级 after/afterName。"
                        + " @ConditionalOnBean 在自动配置被处理的那一刻求值，排序在没有声明时按"
                        + "全限定类名走（com.mall.* 早于 org.springframework.*），"
                        + "所以条件很可能看不到目标 bean，@Bean 被静默跳过。"
                        + " 要么点名 after/afterName，要么把参数改成 ObjectProvider<T>"
                        + "（bean 创建时才解析，与排序无关；调用方是必需注入时只能选这个）。")
                .isEmpty();
    }

    private List<Class<?>> loadRegisteredAutoConfigurations() throws Exception {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        List<Class<?>> types = new ArrayList<>();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as("找不到 %s，自动配置注册文件可能被改名或删除了", resource).isNotNull();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String name = line.trim();
                if (!name.isEmpty() && !name.startsWith("#")) {
                    types.add(Class.forName(name));
                }
            }
        }
        return types;
    }
}

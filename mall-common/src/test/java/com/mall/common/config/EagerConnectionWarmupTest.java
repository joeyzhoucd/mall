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
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 守住启动预热的三条性质。第三条（缺类时仍能启动）是 2026-08-27 真实故障的回归测试。
 *
 * <h3>为什么「预热失败也不能影响启动」要测</h3>
 * 预热本身失败是可以接受的（某个中间件此刻不可用，交给探针和重试处理）。
 * 不可接受的是<b>因为预热失败而让一个功能完好的服务起不来</b> —— 那会把
 * 「中间件抖了一下」放大成「所有服务 crashloop」，比不做预热糟糕得多。
 * 这个风险很容易在后续重构里被引入（去掉 catch），而且只在中间件真出问题那天才暴露。
 *
 * <h3>「缺类时仍能启动」这条是 2026-08-27 真实故障的回归测试</h3>
 * 第一版实现把三个 {@code @Bean} 都放在外层类、只在方法上挂 {@code @ConditionalOnClass}，
 * 结果 <b>9 个服务全部启动失败</b>：
 * <pre>
 * Failed to introspect Class [com.mall.common.config.EagerConnectionWarmup]
 * Caused by: NoClassDefFoundError: org/springframework/amqp/rabbit/connection/ConnectionFactory
 * </pre>
 * 原因是 Spring 要先 {@code Class.getDeclaredMethods()} 才能找到 {@code @Bean} 方法，
 * 而那一步会解析全部方法签名 —— 方法级条件注解还没轮到求值。
 * <p>
 * 这个 bug 在本机测试里<b>不会自然出现</b>，因为 mall-common 自己的测试 classpath 上有 amqp
 * （optional 依赖对声明它的模块本身是可见的）。它只在「不引 amqp 的服务」里发作。
 * 具体的测法和「为什么没用 FilteredClassLoader」见
 * {@link #outerClassMustNotReferenceOptionalDependencyTypes()} 的注释。
 * <p>
 * 另一条更贵的教训：出事后我<b>只验证了 mall-coupon</b>（唯一有 amqp、因而恰好不受影响的
 * 服务），就以为整批都好了。而 K8s 的滚动更新保留了旧 pod 继续提供服务，
 * 外部看起来一切正常，ArgoCD 只是显示 Degraded。
 * <b>验证要覆盖「条件不同的那一类」，不是随便挑一个。</b>
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

    /**
     * 只有 optional 依赖的类型出现在【外层类的方法签名】上时才会触发那次故障，
     * 所以直接检查这条结构规则，而不是试图在测试里复现启动过程。
     * <p>
     * 为什么不用 FilteredClassLoader：实测它抓不到这个 bug。它只在被问到隐藏类时
     * 抛 ClassNotFoundException，其余全部委托给父加载器 —— 于是
     * EagerConnectionWarmup 本身仍由父加载器加载，方法签名照样能解析成功。
     * 要真正复现需要一个「子优先且屏蔽 amqp」的自定义类加载器，那既脆弱又难读。
     * <p>
     * 换成检查结构规则反而更好：它直接表达了那条必须遵守的约束
     * （引用可能不存在的类的 @Bean 必须放进带类级 @ConditionalOnClass 的嵌套类），
     * 而且这条测试已经验证过 —— 对旧实现失败、对现在的实现通过。
     */
    @Test
    @DisplayName("外层类的方法签名不得引用 optional 依赖的类型（9 服务崩溃的回归测试）")
    void outerClassMustNotReferenceOptionalDependencyTypes() {
        // mall-common 里声明成 optional 的两个依赖：不用 MQ / 不用 Redis 的服务
        // classpath 上没有这些包，而 Spring 为了找 @Bean 方法会调 getDeclaredMethods()，
        // 那一步会解析【全部方法签名】—— 方法级的 @ConditionalOnClass 还没轮到求值。
        List<String> optionalPackages = List.of("org.springframework.amqp.", "org.springframework.data.redis.");

        for (Method method : EagerConnectionWarmup.class.getDeclaredMethods()) {
            List<Class<?>> types = new java.util.ArrayList<>(Arrays.asList(method.getParameterTypes()));
            types.add(method.getReturnType());
            for (Class<?> type : types) {
                String name = type.getName();
                for (String pkg : optionalPackages) {
                    assertThat(name)
                            .as("EagerConnectionWarmup.%s 的签名里出现了 optional 依赖的类型 %s。"
                                    + " 不引这个依赖的服务会在自省这个类时抛 NoClassDefFoundError 而整体启动失败"
                                    + "（2026-08-27 实际导致 9 个服务 CrashLoopBackOff）。"
                                    + " 把这个 @Bean 移到带类级 @ConditionalOnClass 的嵌套静态类里。",
                                    method.getName(), name)
                            .doesNotStartWith(pkg);
                }
            }
        }
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

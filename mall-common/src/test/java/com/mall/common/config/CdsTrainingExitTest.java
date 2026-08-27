package com.mall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住 {@link CdsTrainingExit} 的开关语义。
 *
 * <h3>为什么这条测试的价值远超它的体量</h3>
 * 这个监听器做的事是退出 JVM。它只该在镜像构建期的 CDS 训练运行里生效。
 * 如果哪天它被默认打开（比如有人把 {@code havingValue = "true"} 改成
 * {@code matchIfMissing = true}，或者不小心把开关配进了 application.yml），
 * <b>每个服务一启动就会立刻退出</b>，表现是全线无限 CrashLoopBackOff。
 * <p>
 * 而这个故障的排查体验极差：容器退出码是 0（正常退出），日志里也只有一行
 * "上下文已 refresh 完成，立即退出"，看起来像"启动完就结束了"，
 * 很容易被当成健康检查或启动参数的问题。所以那条日志里特意写了
 * "如果你在生产环境看到这行日志，说明 mall.cds.training-exit 被误开了"。
 * <p>
 * 本仓库刚刚为一个自动配置的位置问题付过 9 个服务 CrashLoopBackOff 的学费
 * （见 {@link AutoConfigurationSignatureTest}），同类风险不想再来一次。
 *
 * <h3>为什么必须注入一个假的退出动作</h3>
 * 这个监听器挂在 {@code ContextRefreshedEvent} 上，而 {@link ApplicationContextRunner}
 * 的 {@code run()} 就会刷新上下文 —— 也就是<b>测试一跑就会触发它</b>。
 * 第一版实现把 {@code System.exit(0)} 写死在监听器里，结果这条测试直接把测试 JVM 打死了
 * （surefire 报 "forked VM terminated without properly saying goodbye"）。
 * <p>
 * 所以退出动作被抽成了 {@link CdsTrainingExit.CdsExitAction} bean，测试注入一个只计数的
 * 实现。这样验证的是<b>完整接线</b>（属性 -> 自动配置 -> 监听器 -> 退出动作被调用），
 * 而不只是"bean 在不在"。
 */
class CdsTrainingExitTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CdsTrainingExit.class));

    @Test
    @DisplayName("默认不注册退出监听器（否则服务一启动就退出，全线 CrashLoop）")
    void disabledByDefault() {
        runner.withUserConfiguration(FakeExitConfig.class).run(context -> {
            assertThat(context)
                    .as("默认注册了退出监听器 —— 每个服务启动后会立刻退出，"
                            + "表现为全线无限 CrashLoopBackOff，而容器退出码是 0、日志只有一行，"
                            + "极难往「某个开关默认开了」这个方向想")
                    .doesNotHaveBean(ApplicationListener.class);
            assertThat(FakeExitConfig.calls.get()).as("默认情况下退出动作被调用了").isZero();
        });
    }

    @Test
    @DisplayName("显式配成 false 时同样不注册")
    void notRegisteredWhenExplicitlyFalse() {
        runner.withUserConfiguration(FakeExitConfig.class)
                .withPropertyValues("mall.cds.training-exit=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ApplicationListener.class);
                    assertThat(FakeExitConfig.calls.get()).isZero();
                });
    }

    @Test
    @DisplayName("配成 true 时：注册监听器，并且 refresh 完成后真的调用退出动作")
    void exitsOnRefreshWhenEnabled() {
        runner.withUserConfiguration(FakeExitConfig.class)
                .withPropertyValues("mall.cds.training-exit=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ApplicationListener.class);
                    // 这一条才是关键：不只是 bean 存在，而是 refresh 之后退出动作确实被调了。
                    // 只断言 bean 存在的话，监听器接错事件、或者退出动作没被调用，
                    // 测试照样通过 —— 而那时构建会一直卡到超时。
                    assertThat(FakeExitConfig.calls.get())
                            .as("refresh 完成后没有调用退出动作 —— CDS 训练运行不会退出，"
                                    + "镜像构建会一直卡到超时")
                            .isEqualTo(1);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class FakeExitConfig {

        static final AtomicInteger calls = new AtomicInteger();

        FakeExitConfig() {
            calls.set(0);
        }

        /** 替换掉真正会退出 JVM 的那个 bean（生产实现上有 @ConditionalOnMissingBean）。 */
        @Bean
        CdsTrainingExit.CdsExitAction cdsExitAction() {
            return calls::incrementAndGet;
        }
    }
}

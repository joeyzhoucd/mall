package com.mall.common.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住分页拦截器一定会被注册。
 *
 * <h3>为什么值得单独一条测试</h3>
 * 没有 {@link PaginationInnerInterceptor} 时，MyBatis-Plus 的 selectPage <b>不报错</b>，
 * 而是把全表查出来并让 total 恒为 0。前端表现是「有数据但显示共 0 条、翻页失效」，
 * 数据量小的时候几乎看不出来，数据量一大就变成全表扫描。
 * <p>
 * 这个仓库里 6 个用 MyBatis-Plus 的服务曾有 5 个漏配 —— 说明靠人记得是不行的。
 * 现在改成 mall-common 的自动配置，这条测试保证它不会在某次重构里被悄悄弄丢。
 * <p>
 * 用 ApplicationContextRunner 而不是 &#64;SpringBootTest：只装配这一个自动配置类，
 * 不需要数据库、不启动完整上下文，毫秒级，本地和 CI 都能跑。
 */
class MybatisPlusAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MybatisPlusAutoConfiguration.class));

    @Test
    @DisplayName("默认注册一个带分页拦截器的 MybatisPlusInterceptor")
    void registersPaginationInterceptorByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
            // 光有 MybatisPlusInterceptor 这个壳没用，必须确认里面真的装了分页拦截器 ——
            // 一个空壳同样不会报错，同样让 total 恒为 0。
            assertThat(interceptor.getInterceptors())
                    .as("MybatisPlusInterceptor 里没有 PaginationInnerInterceptor，分页不会生效")
                    .hasAtLeastOneElementOfType(PaginationInnerInterceptor.class);
        });
    }

    @Test
    @DisplayName("服务已自己配了拦截器时让位，不覆盖")
    void backsOffWhenServiceDefinesItsOwn() {
        runner.withUserConfiguration(CustomInterceptorConfig.class).run(context -> {
            assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);
            // mall-product 有自己的 MybatisPlusConfig（还兼做 @MapperScan），
            // 自动配置必须让位，否则会变成两个 bean 冲突或覆盖掉服务的自定义设置。
            assertThat(context.getBean(MybatisPlusInterceptor.class))
                    .as("自动配置没有让位给服务自己定义的拦截器")
                    .isSameAs(context.getBean(CustomInterceptorConfig.class).custom);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomInterceptorConfig {

        final MybatisPlusInterceptor custom = new MybatisPlusInterceptor();

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            return custom;
        }
    }

    /** 仅用于让上面的类型断言读起来更清楚。 */
    @SuppressWarnings("unused")
    private static Class<? extends InnerInterceptor> paginationType() {
        return PaginationInnerInterceptor.class;
    }
}

package com.mall.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 给每个引入了 springdoc 的服务一份像样的 OpenAPI 描述。
 *
 * <h3>为什么需要这个类 —— springdoc 自己给不出服务名</h3>
 * 不配的话每个服务的文档标题都是同一句 {@code "OpenAPI definition"}，版本是 {@code "v0"}。
 * 十来个服务的文档摆在一起分不出谁是谁。而 springdoc <b>没有</b>用属性设置 info 的办法
 * （{@code springdoc.info.title} 这种键不存在），只能给一个 {@code OpenAPI} bean。
 * 放在 mall-common 里，各服务就不用各写一份。
 *
 * <h3>为什么是 optional 依赖 + 各服务显式引入</h3>
 * mall-common 把 springdoc 声明成 {@code <optional>true</optional>}，<b>不传递</b>。
 * 想要 API 文档的服务自己引一遍。
 * <p>
 * 刻意不做成传递依赖：mall-common 曾经把 MyBatis-Plus + JDBC + MySQL 驱动传给了
 * <b>并不连数据库</b>的 mall-gateway / mall-auth / mall-cart，它们只能靠
 * {@code exclude = DataSourceAutoConfiguration.class} 绕，jar 里白白多背 7–8 MiB。
 * 那个问题刚清理完，不该转头用同样的方式撒一个新的。
 * <p>
 * 具体不给谁：
 * <ul>
 *   <li><b>mall-admin</b> —— 它是<b>契约优先</b>的（手写 {@code openapi/admin-api.yaml}
 *       + {@code OpenApiContractTest} 双向核对）。再加一份代码优先生成的文档，
 *       等于同一个服务有两个互不校验的「真相」。</li>
 *   <li><b>mall-gateway</b> —— WebFlux 栈，webmvc 版的 springdoc 自动配置压根不会激活，
 *       引进去只是白增体积；而且网关是路由，没有自己的业务接口。</li>
 *   <li><b>mall-config</b> —— Config Server，没有业务接口。</li>
 * </ul>
 *
 * <h3>@Bean 为什么必须放在嵌套类里</h3>
 * 这是 2026-08-27 那次<b>一次性把 9 个服务打进 CrashLoopBackOff</b> 的教训：
 * Spring 处理一个配置类时会调 {@code Class.getDeclaredMethods()}，而那一步会
 * <b>解析每个方法签名上的全部类型</b>。方法上挂 {@code @ConditionalOnClass} 是没用的 ——
 * 条件还没来得及判断，类型解析就已经抛 {@code NoClassDefFoundError} 了。
 * 所以引用了可能不存在的类型（这里是 {@code io.swagger.v3.oas.models.OpenAPI}）的
 * {@code @Bean}，必须放进<b>带类级 {@code @ConditionalOnClass} 的嵌套静态类</b>。
 * {@code AutoConfigurationSignatureTest} 会守着这条。
 *
 * <h3>启动开销</h3>
 * springdoc 默认<b>不在启动时扫描控制器</b>，OpenAPI 文档是第一次访问
 * {@code /v3/api-docs} 时才构建并缓存的。所以对冷启动的影响只有自动配置本身那点，
 * 和这个项目正在做的 CDS / 连接预热不冲突。
 *
 * <h3>暴露面</h3>
 * {@code /v3/api-docs} 和 {@code /swagger-ui/**} 都<b>不在网关的路由规则里</b>
 * （网关只转发 {@code /api/**} 和几个 host 规则），所以默认只在集群内可达。
 * 哪天要对外开，记得先加鉴权 —— 接口文档等于把攻击面画成了地图。
 */
@AutoConfiguration
public class MallOpenApiAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAPI.class)
    public static class OpenApiInfoConfiguration {

        /**
         * 用 spring.application.name 当标题，省得每个服务各写一份。
         * 取不到时回落到一个明显不对的名字，而不是空字符串 ——
         * 空标题在 Swagger UI 上看不出异常，"unknown-service" 一眼就知道配置没生效。
         */
        @Bean
        @ConditionalOnMissingBean
        public OpenAPI mallOpenApi(@Value("${spring.application.name:unknown-service}") String appName) {
            return new OpenAPI().info(new Info()
                    .title(appName + " API")
                    .version("v1")
                    .description("mall 微服务 " + appName + " 的接口文档。"
                            + "由 springdoc 从代码生成，未经人工校对 —— "
                            + "和 mall-admin 那份手写并有测试双向核对的契约不是一回事。"));
        }
    }
}

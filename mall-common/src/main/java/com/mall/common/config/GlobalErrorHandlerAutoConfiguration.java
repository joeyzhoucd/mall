package com.mall.common.config;

import com.mall.common.web.UnhandledExceptionAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 把 {@link UnhandledExceptionAdvice} 装到每一个 Servlet 服务上。
 *
 * <h3>为什么要走自动配置，而不是让各模块自己 @Import</h3>
 * 因为「各模块自己加」正是现在这个状态：11 个有 controller 的模块里 8 个没有，
 * 而有的那 3 个也只处理参数校验。靠人记得加，就会继续漏，
 * 而漏掉的表现是「500 没有任何日志」—— 没有任何征兆提醒你漏了。
 *
 * <h3>三个条件各自挡住什么</h3>
 * <ul>
 *   <li>{@code @ConditionalOnWebApplication(SERVLET)} —— mall-gateway 是 WebFlux，
 *       {@code @RestControllerAdvice} 那一套在它上面不适用；
 *       不加这个条件会在网关里装上一个永远不生效的 bean（无害但误导），
 *       而且 {@code HttpServletRequest} 这个参数类型在纯响应式栈里根本不存在。
 *       网关的兜底是另一件事（WebFlux 要写 {@code ErrorWebExceptionHandler}），
 *       <b>这次没做</b>。</li>
 *   <li>{@code @ConditionalOnClass} —— mall-common 被一些非 Web 模块依赖
 *       （比如纯 starter），classpath 上没有 spring-web 时不该尝试加载。
 *       这个项目已经踩过一次同类的坑：
 *       {@code spring-boot-starter-data-redis} 在 mall-common 里是
 *       {@code <optional>true</optional>}、不传递，而某个
 *       {@code @ConditionalOnClass(StringRedisTemplate.class)} 就此静默跳过，
 *       表现是 mall-ware 起不来。</li>
 *   <li>{@code @ConditionalOnProperty(matchIfMissing = true)} —— 默认开。
 *       留一个关掉的开关，是为了万一某个服务想完全自己接管异常处理
 *       （目前没有这种服务）。</li>
 * </ul>
 *
 * <h3>{@code @ConditionalOnMissingBean} 的含义</h3>
 * 某个模块如果自己定义了一个同类型的兜底 advice，就用它自己的。
 * 注意它<b>按类型</b>判断，所以 product/order/payment 那三个
 * 各自不同类型的处理器不会让这个条件失效 —— 它们和这里是
 * 「共存 + 排序」的关系，不是替代关系（见 {@link UnhandledExceptionAdvice} 的注释）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.bind.annotation.RestControllerAdvice",
        "jakarta.servlet.http.HttpServletRequest",
})
@ConditionalOnProperty(name = "mall.web.unhandled-exception-advice.enabled",
        havingValue = "true", matchIfMissing = true)
public class GlobalErrorHandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UnhandledExceptionAdvice mallUnhandledExceptionAdvice() {
        return new UnhandledExceptionAdvice();
    }
}

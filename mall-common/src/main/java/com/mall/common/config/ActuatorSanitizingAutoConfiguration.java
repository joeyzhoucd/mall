package com.mall.common.config;

import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 给 actuator 的 {@code /actuator/env} 和 {@code /actuator/configprops} 补上敏感值脱敏。
 *
 * <h3>为什么需要它：Boot 4 在 show-values=always 下【不带任何默认脱敏】</h3>
 * mall-common-default.properties 里把两个端点设成了
 * {@code show-values=always}（理由见那里：Config Server / K8s 环境变量 /
 * 本文件 / 服务自己的 yml 四层叠加，"最终哪个值赢了"原来无从查证）。
 * <p>
 * 但 2026-09-08 实测发现代价没被算到：在运行中的 mall-product pod 上打
 * {@code /actuator/env}，这三个键是<b>明文</b>的 ——
 * <pre>
 * systemEnvironment  MYSQL_PASSWORD           = "root"
 * systemEnvironment  RABBITMQ_PASSWORD        = "guest"
 * application.yml    spring.datasource.password = "root"
 * </pre>
 *
 * <h3>show-values 是二元的，所以不能靠调它来解决</h3>
 * 从 spring-boot-actuator 4.1.1 的 {@code Sanitizer.sanitize} 字节码确认：
 * <pre>
 * 11: iload_2          // showUnsanitized
 * 12: ifne 18          // 为真 -> 跳到 18
 * 15: ldc "******"     // 为假 -> 【所有】值一律打码
 * 18: ... 遍历 sanitizingFunctions 逐个 applyUnlessFiltered ...
 * </pre>
 * 也就是说 {@code never} / 未授权的 {@code when-authorized} 会把<b>全部</b>值
 * 打成 {@code ******}，不只是敏感的那些 —— 那就退回"只能看到有哪些属性、
 * 看不到任何值"，把当初设 always 的理由全部作废。
 * <p>
 * 而同一段字节码也说明：{@code always} 时 <b>SanitizingFunction 照样会被应用</b>。
 * 所以正确的做法不是改 show-values，而是<b>补上一个脱敏函数</b>：
 * 普通值照常可见（诊断能力保住），敏感键单独打码（凭据不再外泄）。
 *
 * <h3>这条也让「上云前必须改回 never」这个待办降级了</h3>
 * 原注释写着上云前必须改回去，因为 env 里能读到数据库密码和 JWT 密钥。
 * 补上脱敏之后凭据不在里面了。<b>但不等于可以直接上云</b>：
 * env / configprops / beans / mappings 仍然会暴露内部结构，
 * 上云时该做的是给 actuator 加认证（那时 {@code when-authorized} 才有意义 ——
 * 在没有认证的现在它等价于 never）。
 *
 * <h3>为什么用 ifLikelySensitive() 而不是自己列键名</h3>
 * 自己列清单等于承担"每加一个新的敏感配置项都要记得回来补"的义务，
 * 而忘记的后果是静默泄露。Boot 自带的这组判定覆盖 credential / uri /
 * 敏感属性名 / vcap 四类模式，新增的 {@code *.password} / {@code *.secret}
 * 会自动被覆盖。
 * <p>
 * 但"自动覆盖"这件事本身要被验证，不能假设 —— 见
 * ActuatorSanitizingAutoConfigurationTest，它用<b>实测泄露的那三个真实键名</b>
 * 断言确实被打码了，而不是断言"函数注册上了"。
 */
@AutoConfiguration
@ConditionalOnClass(SanitizingFunction.class)
public class ActuatorSanitizingAutoConfiguration {

    /**
     * 敏感值脱敏。
     *
     * <p>{@code @ConditionalOnMissingBean} 让服务可以自己换一个更严格的实现，
     * 但<b>不会</b>因为某个服务没声明就没有 —— 默认存在，要覆盖得显式做。
     * 这个方向上的默认值决定了"忘记"的后果是什么。
     */
    @Bean
    @ConditionalOnMissingBean(name = "mallSensitiveValueSanitizer")
    public SanitizingFunction mallSensitiveValueSanitizer() {
        return SanitizingFunction.sanitizeValue().ifLikelySensitive();
    }
}

package com.mall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 守住 {@code /actuator/env} 不会明文吐出凭据。
 *
 * <h3>断言的是【实测泄露过的那三个真实键名】，不是"函数注册上了"</h3>
 * 2026-09-08 在运行中的 mall-product pod 上打 /actuator/env，这三个是明文：
 * <pre>
 * MYSQL_PASSWORD             = "root"
 * RABBITMQ_PASSWORD          = "guest"
 * spring.datasource.password = "root"
 * </pre>
 * 断言"注册了一个 SanitizingFunction"是没有意义的 —— 它无法回答
 * 「这个函数到底管不管 MYSQL_PASSWORD 这种全大写的环境变量名」。
 * 所以这里直接把这三个键喂进去，看输出是不是 {@code ******}。
 *
 * <h3>顺带把 Boot 的行为钉住</h3>
 * 整个方案成立的前提是：{@code show-values=always} 时脱敏函数<b>照样</b>会被应用。
 * 这一点是从 Sanitizer.sanitize 的字节码确认的，下面用
 * {@code showUnsanitized=true} 走一遍真实的 Sanitizer 来验证 ——
 * 如果哪天 Boot 改成"always 就完全跳过函数"，这些断言会失败，
 * 而那正是需要有人重新设计的时刻。
 */
class ActuatorSanitizingAutoConfigurationTest {

    private static final PropertySource<?> SOURCE =
            new MapPropertySource("test", Map.of("k", "v"));

    /** 用真实的 Sanitizer，而不是直接调函数 —— 要验证的是端点实际走的那条路。 */
    private static Object sanitize(String key, Object value) {
        SanitizingFunction function =
                new ActuatorSanitizingAutoConfiguration().mallSensitiveValueSanitizer();
        Sanitizer sanitizer = new Sanitizer(List.of(function));
        // true = show-values 为 always 时传的那个值。
        return sanitizer.sanitize(new SanitizableData(SOURCE, key, value), true);
    }

    @Test
    @DisplayName("实测泄露过的三个键必须被打码")
    void theThreeKeysThatActuallyLeakedAreMasked() {
        assertEquals(SanitizableData.SANITIZED_VALUE, sanitize("MYSQL_PASSWORD", "root"),
                "MYSQL_PASSWORD 仍是明文 —— 这正是 2026-09-08 实测泄露的那个键");
        assertEquals(SanitizableData.SANITIZED_VALUE, sanitize("RABBITMQ_PASSWORD", "guest"),
                "RABBITMQ_PASSWORD 仍是明文");
        assertEquals(SanitizableData.SANITIZED_VALUE, sanitize("spring.datasource.password", "root"),
                "spring.datasource.password 仍是明文");
    }

    /**
     * JWT 密钥没在实测里露出来（那个服务没有它），但它是这套里最严重的一个：
     * 拿到就能伪造管理端令牌。所以单独守一次。
     */
    @Test
    @DisplayName("管理端 JWT 密钥也必须被打码 —— 拿到它就能伪造令牌")
    void adminJwtSecretIsMasked() {
        assertEquals(SanitizableData.SANITIZED_VALUE,
                sanitize("mall.admin.jwt.secret", "local-dev-only-do-not-use-in-any-real-environment"),
                "JWT 密钥仍是明文");
    }

    @Test
    @DisplayName("对象存储的 access key / secret 也要打码")
    void storageCredentialsAreMasked() {
        assertEquals(SanitizableData.SANITIZED_VALUE, sanitize("mall.storage.access-key", "AKIAIOSFODNN7"),
                "对象存储 access key 仍是明文");
        assertEquals(SanitizableData.SANITIZED_VALUE, sanitize("mall.storage.secret-key", "wJalrXUtnFEMI"),
                "对象存储 secret 仍是明文");
    }

    /**
     * 非敏感值必须<b>照常可见</b>。
     *
     * <p>这条和上面几条同等重要：如果连普通值也被打码，那就等于把
     * show-values 调回了 never，而当初设 always 的全部理由
     * （查"最终哪个值赢了、来自哪一层"）就没了。
     * 这个方案的价值恰恰在于两者兼得。
     */
    @Test
    @DisplayName("非敏感值照常可见 —— 否则等于把 show-values 调回了 never")
    void ordinaryValuesStayVisible() {
        assertEquals("mall-product", sanitize("spring.application.name", "mall-product"));
        assertEquals("5", sanitize("spring.datasource.hikari.maximum-pool-size", "5"));
        assertEquals("true", sanitize("spring.threads.virtual.enabled", "true"));
        assertEquals("consul", sanitize("spring.cloud.consul.host", "consul"));
    }

    /**
     * 反向对照：确认上面的断言真的能失败。
     *
     * <p>不注册任何脱敏函数时（也就是改动前的状态），同一个 Sanitizer
     * 必须把敏感键<b>原样</b>返回。否则说明 Boot 自带了默认脱敏，
     * 那这整个自动配置就是多余的 —— 而"多余的安全措施"会让人误以为
     * 已经有保护，从而不去检查真正的那一层。
     */
    @Test
    @DisplayName("反向对照：不注册脱敏函数时，敏感键确实是明文（说明这个配置不是多余的）")
    void negativeControlWithoutAnySanitizingFunction() {
        Sanitizer bare = new Sanitizer(List.of());
        Object value = bare.sanitize(new SanitizableData(SOURCE, "MYSQL_PASSWORD", "root"), true);

        assertEquals("root", value,
                "Boot 自带了默认脱敏？那 ActuatorSanitizingAutoConfiguration 就是多余的，"
                        + "而一个多余的安全措施会掩盖真正需要检查的那一层");
        assertNotEquals(SanitizableData.SANITIZED_VALUE, value);
    }

    /**
     * 另一半反向对照：show-values 为 never（{@code showUnsanitized=false}）时
     * <b>所有</b>值都被打码，包括非敏感的。
     *
     * <p>这条把"为什么不能靠调 show-values 解决"变成可执行的证据：
     * 调成 never 会连 spring.application.name 一起打码。
     */
    @Test
    @DisplayName("反向对照：show-values=never 会把非敏感值也打码，所以调它解决不了问题")
    void neverMasksEverythingIncludingHarmlessValues() {
        Sanitizer sanitizer = new Sanitizer(
                List.of(new ActuatorSanitizingAutoConfiguration().mallSensitiveValueSanitizer()));

        Object value = sanitizer.sanitize(
                new SanitizableData(SOURCE, "spring.application.name", "mall-product"), false);

        assertEquals(SanitizableData.SANITIZED_VALUE, value,
                "show-values=never 居然没有打码非敏感值 —— 那本类注释里关于"
                        + "「show-values 是二元的」这个判断就需要重新验证");
    }
}

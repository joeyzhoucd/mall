package com.mall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住「网关的验签密钥真的来自 JWT_SECRET，而且和 mall-admin 用的是同一把」。
 *
 * <h3>这条测试防的是一次静默失效（2026-09-03 差点发生）</h3>
 * {@link AdminAuthFilter} 读的属性是 {@code mall.admin.jwt.secret}，
 * 而 K8s 注入的环境变量叫 {@code JWT_SECRET}。
 * <b>Spring 的松散绑定会把 {@code JWT_SECRET} 映射到 {@code jwt.secret}，
 * 而不是 {@code mall.admin.jwt.secret}</b> —— 所以必须在 application.yml 里显式写出
 * {@code secret: ${JWT_SECRET:...}} 这一行，密钥才会真的被注入。
 * <p>
 * 漏掉那一行的后果：网关一直用 yml 里的本地默认值，把 mall-admin 签发的所有真实令牌
 * 都判为无效。表现是「登录成功了，但之后每个 /api 请求都 401」——
 * <b>不报任何错，日志里只有一行「管理端鉴权失败」</b>，
 * 完全不指向「有一行属性映射没写」。我写这个功能时就漏了，靠事后复查才发现。
 *
 * <h3>为什么还要比对两边的默认值</h3>
 * 本地不注入 {@code JWT_SECRET} 时两边各用自己的默认值。这两个默认值只要有一个字符不同，
 * 本地就会复现上面那个 401 症状 —— 而那时候人会去查令牌、查会话、查网关路由，
 * 唯一不会去查的就是「两个 yml 里的占位密钥不一样」。
 */
class AdminJwtSecretWiringTest {

    /** surefire 以模块目录为工作目录，所以这里能用相对路径找到兄弟模块。 */
    private static final Path GATEWAY_YML = Path.of("src/main/resources/application.yml");
    private static final Path ADMIN_YML = Path.of("../mall-admin/src/main/resources/application.yml");

    private static final Pattern SECRET_LINE =
            Pattern.compile("secret:\\s*\\$\\{JWT_SECRET:([^}]*)}");

    private static String read(Path p) throws Exception {
        assertThat(Files.exists(p)).as("找不到 %s —— 路径假设变了，这个测试等于没检查", p).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static String defaultSecretOf(String yml) {
        Matcher m = SECRET_LINE.matcher(yml);
        return m.find() ? m.group(1) : null;
    }

    @Test
    @DisplayName("网关的 application.yml 必须把 JWT_SECRET 显式映射到 mall.admin.jwt.secret")
    void gatewayMapsEnvVarToTheProperty() throws Exception {
        String yml = read(GATEWAY_YML);

        // 属性路径要完整存在，不能只有 JWT_SECRET 这个词出现在别处（比如注释里）。
        assertThat(yml)
                .as("网关 yml 里没有 mall.admin.jwt.secret 的映射。"
                        + "AdminAuthFilter 会一直用本地默认密钥，所有真实令牌都 401，而且不报错。")
                .contains("mall:")
                .contains("admin:")
                .contains("jwt:");
        assertThat(defaultSecretOf(yml))
                .as("网关 yml 里没有 secret: ${JWT_SECRET:...} 这一行")
                .isNotNull();
    }

    @Test
    @DisplayName("过滤器读的属性名必须和 yml 里配的那一个一致")
    void filterReadsTheSameProperty() throws Exception {
        // 直接读过滤器源码里的 @Value，避免「yml 配了 A、代码读 B」这种两边各自看起来都对的情况。
        String src = read(Path.of("src/main/java/com/mall/gateway/filter/AdminAuthFilter.java"));
        assertThat(src)
                .as("AdminAuthFilter 里的 @Value 属性名和 yml 不一致")
                .contains("${mall.admin.jwt.secret:");
    }

    @Test
    @DisplayName("网关和 mall-admin 的本地默认密钥必须逐字一致")
    void localDefaultsAgree() throws Exception {
        String gatewayDefault = defaultSecretOf(read(GATEWAY_YML));
        String adminDefault = defaultSecretOf(read(ADMIN_YML));

        assertThat(adminDefault)
                .as("在 mall-admin 的 yml 里没找到 secret: ${JWT_SECRET:...} —— "
                        + "要么它改了写法，要么这个正则失效了。无论哪种，本测试的前提都没了。")
                .isNotNull();
        assertThat(gatewayDefault)
                .as("两边的本地默认密钥不一致。本地不注入 JWT_SECRET 时会复现"
                        + "「登录成功但每个请求都 401」，而排查时几乎不会怀疑到这里。")
                .isEqualTo(adminDefault);
    }
}

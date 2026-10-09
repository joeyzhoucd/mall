package com.mall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住管理端 JWT 的密钥接线（RS256：mall-admin 私钥签发、网关公钥验签）。
 *
 * <h3>这条测试最初防的是一次静默失效（2026-09-03 差点发生）</h3>
 * 属性叫 {@code mall.admin.jwt.*}，而 K8s 注入的环境变量叫 {@code JWT_...}。
 * <b>Spring 的松散绑定会把 {@code JWT_PUBLIC_KEY} 映射到 {@code jwt.public-key}，
 * 而不是 {@code mall.admin.jwt.public-key}</b> —— 必须在 application.yml 里显式写出映射，
 * 否则网关拿不到公钥。当年漏的是 HS256 的 secret 那一行，表现是「登录成功但每个 /api 请求都 401」，不报任何错。
 *
 * <h3>2026-10-09：HS256 移除之后多守一条</h3>
 * 过渡期结束，HS256 的 secret 和它在仓库里的公开默认值
 * {@code local-dev-only-do-not-use-in-any-real-environment} 一起删了。
 * 它们不能回来：那个默认值意味着环境变量一漏配，签发和验签就都用一把人人可见的密钥。
 */
class AdminJwtSecretWiringTest {

    /** surefire 以模块目录为工作目录，所以这里能用相对路径找到兄弟模块。 */
    private static final Path GATEWAY_YML = Path.of("src/main/resources/application.yml");
    private static final Path ADMIN_YML = Path.of("../mall-admin/src/main/resources/application.yml");
    private static final Path FILTER_SRC = Path.of("src/main/java/com/mall/gateway/filter/AdminAuthFilter.java");

    private static final String PUBLIC_DEFAULT = "local-dev-only-do-not-use-in-any-real-environment";

    private static String read(Path p) throws Exception {
        assertThat(Files.exists(p)).as("找不到 %s —— 路径假设变了，这个测试等于没检查", p).isTrue();
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /** 去掉 yml 的 # 注释行，只看真正生效的配置（注释里提到旧名字是允许的）。 */
    private static String config(String yml) {
        return Pattern.compile("^\\s*#.*$", Pattern.MULTILINE).matcher(yml).replaceAll("");
    }

    @Test
    @DisplayName("网关的 application.yml 必须把 JWT_PUBLIC_KEY 显式映射到 mall.admin.jwt.public-key")
    void gatewayMapsPublicKey() throws Exception {
        String yml = config(read(GATEWAY_YML));
        assertThat(yml).contains("mall:").contains("admin:").contains("jwt:");
        assertThat(yml).as("网关 yml 里没有 public-key: ${JWT_PUBLIC_KEY...} —— 网关拿不到公钥")
                .containsPattern("public-key:\\s*\\$\\{JWT_PUBLIC_KEY[:}]");
    }

    @Test
    @DisplayName("过滤器读的属性名必须和 yml 里配的那一个一致")
    void filterReadsTheSameProperty() throws Exception {
        assertThat(read(FILTER_SRC)).as("AdminAuthFilter 里的 @Value 属性名和 yml 不一致")
                .contains("${mall.admin.jwt.public-key");
    }

    @Test
    @DisplayName("mall-admin 的 application.yml 必须把 JWT_PRIVATE_KEY 显式映射到 mall.admin.jwt.private-key")
    void adminMapsPrivateKey() throws Exception {
        assertThat(config(read(ADMIN_YML))).as("mall-admin yml 里没有 private-key: ${JWT_PRIVATE_KEY...}")
                .containsPattern("private-key:\\s*\\$\\{JWT_PRIVATE_KEY[:}]");
    }

    @Test
    @DisplayName("HS256 的 secret 和它的公开默认值不能回来（网关、mall-admin、过滤器源码）")
    void hs256SecretIsGone() throws Exception {
        for (Path p : new Path[]{GATEWAY_YML, ADMIN_YML}) {
            String cfg = config(read(p));
            assertThat(cfg).as("%s 里又出现了 JWT_SECRET 映射", p).doesNotContain("JWT_SECRET");
            assertThat(cfg).as("%s 里又出现了公开的默认密钥", p).doesNotContain(PUBLIC_DEFAULT);
        }
        String src = read(FILTER_SRC);
        assertThat(src).doesNotContain("${mall.admin.jwt.secret");
    }
}

package com.mall.admin.security;

import com.mall.admin.config.AdminProperties;
import com.mall.common.security.AdminTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 签发端（{@link JwtService}，用 Nimbus）和校验端（{@link AdminTokenVerifier}，手写 RS256）
 * 必须永远一致。
 *
 * <h3>为什么需要这条测试</h3>
 * 令牌的签发和校验<b>是两套独立实现，跑在不同的服务里</b>：
 * mall-admin 用 spring-security-oauth2-jose 的 Nimbus 签发；
 * mall-gateway 和各服务用 mall-common 里手写的 {@code AdminTokenVerifier} 验签
 * （刻意不给网关引 Spring Security，理由见那个类的注释）。
 * <p>
 * 两套实现意味着<b>它们可以悄悄漂移</b>：改了 claim 名字、换了算法、
 * 调整了 base64 的 padding，任何一处不一致的表现都是
 * 「登录成功，但之后每个管理端请求都 401」—— 而那看起来像令牌坏了或者会话丢了，
 * 很难指向「两个模块对格式的理解不同」。
 * <p>
 * 这条测试放在 mall-admin 是因为<b>只有它同时拥有两边的 classpath</b>。
 * 它不是在测某个类的行为，而是在测两个模块之间的一个约定。
 * 2026-10-09 起只有 RS256（HS256 及其公开默认密钥已删除）。
 */
class AdminTokenCrossCheckTest {

    private static final KeyPair RSA = rsa();

    private static KeyPair rsa() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 和部署时 JWT_PRIVATE_KEY 同形状：PKCS#8 PEM。 */
    private static String privatePem(KeyPair kp) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static JwtService issuer(String privateKey) {
        return new JwtService(new AdminProperties(
                new AdminProperties.Jwt(3600, privateKey),
                new AdminProperties.Captcha(300)));
    }

    private static JwtService issuer() {
        return issuer(privatePem(RSA));
    }

    @Test
    @DisplayName("Nimbus 签发的 RS256 令牌，手写校验器【只拿公钥】必须认")
    void verifierAcceptsIssuedToken() {
        JwtService issuer = issuer();
        String token = issuer.issue(4242L, "cross-admin");

        AdminTokenVerifier.Identity id = new AdminTokenVerifier(issuer.publicKeyBase64()).verify(token);

        assertThat(id)
                .as("网关/各服务验不过 mall-admin 签发的令牌 —— 两侧实现已经漂移，"
                        + "线上表现会是「登录成功但每个请求都 401」")
                .isNotNull();
        assertThat(id.userId()).isEqualTo(4242L);
        assertThat(id.username()).isEqualTo("cross-admin");
        assertThat(issuer.publicKeyBase64())
                .as("公钥由私钥推导，必须和生成时的公钥一致（部署时 JWT_PUBLIC_KEY 就填它）")
                .isEqualTo(Base64.getEncoder().encodeToString(RSA.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("负控制：换一把公钥就必须验不过")
    void verifierRejectsTokenFromAnotherKey() {
        // 没有这条，一个「无脑返回 Identity」的校验器也能让上一条通过。
        String token = issuer().issue(1L, "admin");
        AdminTokenVerifier other = new AdminTokenVerifier(Base64.getEncoder().encodeToString(rsa().getPublic().getEncoded()));
        assertThat(other.verify(token)).isNull();
    }

    @Test
    @DisplayName("mall-admin 自己也只认 RS256：HS256 令牌（含用已删除的公开默认值签的）一律不认")
    void adminRejectsHs256() throws Exception {
        for (String secret : new String[]{"local-dev-only-do-not-use-in-any-real-environment",
                "any-other-secret-that-is-long-enough-32b!!"}) {
            String header = b64("{\"alg\":\"HS256\"}");
            String payload = b64("{\"sub\":\"admin\",\"uid\":1,\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String token = header + "." + payload + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(
                            mac.doFinal((header + "." + payload).getBytes(StandardCharsets.US_ASCII)));
            assertThat(issuer().parse(token)).as("secret=%s", secret).isNull();
        }
    }

    @Test
    @DisplayName("没有私钥（或未解析的占位符、格式不对）时 mall-admin 启动即失败，不存在兜底密钥")
    void failsFastWithoutPrivateKey() {
        assertThatThrownBy(() -> issuer(null)).hasMessageContaining("JWT_PRIVATE_KEY");
        assertThatThrownBy(() -> issuer("")).hasMessageContaining("JWT_PRIVATE_KEY");
        assertThatThrownBy(() -> issuer("${JWT_PRIVATE_KEY}")).hasMessageContaining("JWT_PRIVATE_KEY");
        assertThatThrownBy(() -> issuer("not-a-key")).hasMessageContaining("JWT_PRIVATE_KEY");
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}

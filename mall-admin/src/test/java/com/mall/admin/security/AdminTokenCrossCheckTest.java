package com.mall.admin.security;

import com.mall.admin.config.AdminProperties;
import com.mall.common.security.AdminTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 签发端（{@link JwtService}，用 Nimbus）和校验端（{@link AdminTokenVerifier}，手写 HS256）
 * 必须永远一致。
 *
 * <h3>为什么需要这条测试</h3>
 * 令牌的签发和校验<b>是两套独立实现，跑在两个不同的服务里</b>：
 * mall-admin 用 spring-security-oauth2-jose 的 Nimbus 签发；
 * mall-gateway 用 mall-common 里手写的 {@code AdminTokenVerifier} 验签
 * （刻意不给网关引 Spring Security，理由见那个类的注释）。
 * <p>
 * 两套实现意味着<b>它们可以悄悄漂移</b>：改了 claim 名字、换了算法、
 * 调整了 base64 的 padding，任何一处不一致的表现都是
 * 「登录成功，但之后每个管理端请求都 401」—— 而那看起来像令牌坏了或者会话丢了，
 * 很难指向「两个模块对格式的理解不同」。
 * <p>
 * 这条测试放在 mall-admin 是因为<b>只有它同时拥有两边的 classpath</b>。
 * 它不是在测某个类的行为，而是在测两个模块之间的一个约定。
 */
class AdminTokenCrossCheckTest {

    /** 32 字节以上，两边的下限一致。 */
    private static final String SECRET = "cross-check-secret-at-least-32-bytes!!";

    private JwtService issuer() {
        return new JwtService(new AdminProperties(
                new AdminProperties.Jwt(SECRET, 3600, null),
                new AdminProperties.Captcha(300)));
    }

    @Test
    @DisplayName("Nimbus 签发的令牌，手写校验器必须认")
    void verifierAcceptsIssuedToken() {
        String token = issuer().issue(4242L, "cross-admin");

        AdminTokenVerifier.Identity id = new AdminTokenVerifier(SECRET).verify(token);

        assertThat(id)
                .as("网关验不过 mall-admin 签发的令牌 —— 两侧实现已经漂移，"
                        + "线上表现会是「登录成功但每个请求都 401」")
                .isNotNull();
        assertThat(id.userId()).isEqualTo(4242L);
        assertThat(id.username()).isEqualTo("cross-admin");
    }

    @Test
    @DisplayName("负控制：换一把密钥就必须验不过")
    void verifierRejectsTokenSignedWithAnotherSecret() {
        // 没有这条，一个「无脑返回 Identity」的校验器也能让上一条通过。
        String token = issuer().issue(1L, "admin");

        AdminTokenVerifier other = new AdminTokenVerifier("a-completely-different-secret-32b+!!!");

        assertThat(other.verify(token)).isNull();
    }

    // ------------------------------------------------------------------ RS256（2026-10-07）

    private static final java.security.KeyPair RSA = rsa();

    private static java.security.KeyPair rsa() {
        try {
            java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 和部署时 JWT_PRIVATE_KEY 同形状：PKCS#8 PEM。 */
    private static String privatePem() {
        return "-----BEGIN PRIVATE KEY-----\n"
                + java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(RSA.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
    }

    private JwtService rsaIssuer() {
        return new JwtService(new AdminProperties(
                new AdminProperties.Jwt(SECRET, 3600, privatePem()),
                new AdminProperties.Captcha(300)));
    }

    @Test
    @DisplayName("RS256：Nimbus 签发，手写校验器【只拿公钥】必须认")
    void verifierWithOnlyPublicKeyAcceptsRs256IssuedToken() {
        JwtService issuer = rsaIssuer();
        String token = issuer.issue(4242L, "cross-admin");

        AdminTokenVerifier.Identity id = new AdminTokenVerifier(issuer.publicKeyBase64(), null).verify(token);

        assertThat(id).as("各服务只配公钥时验不过 mall-admin 签发的 RS256 令牌 —— 两侧实现已经漂移").isNotNull();
        assertThat(id.userId()).isEqualTo(4242L);
        assertThat(id.username()).isEqualTo("cross-admin");
        assertThat(issuer.publicKeyBase64())
                .as("公钥由私钥推导，必须和生成时的公钥一致（部署时 JWT_PUBLIC_KEY 就填它）")
                .isEqualTo(java.util.Base64.getEncoder().encodeToString(RSA.getPublic().getEncoded()));
    }

    @Test
    @DisplayName("RS256 签发后，旧 HS256 密钥再也验不过新令牌（签名能力不在 HS256 那边了）")
    void legacyHs256VerifierRejectsRs256Token() {
        String token = rsaIssuer().issue(1L, "admin");
        assertThat(new AdminTokenVerifier(SECRET).verify(token)).isNull();
    }

    @Test
    @DisplayName("过渡期：mall-admin 自己同时认新签的 RS256 和切换前签的 HS256")
    void adminParsesBothDuringTransition() {
        JwtService rsa = rsaIssuer();
        String oldHs256 = issuer().issue(7L, "old-session");
        String newRs256 = rsa.issue(8L, "new-session");

        assertThat(rsa.parse(newRs256)).isNotNull();
        assertThat(rsa.parse(oldHs256)).as("切换前登录的管理员在令牌过期前不该被踢出").isNotNull();
        assertThat(issuer().parse(newRs256)).as("没配私钥的 mall-admin 不该认 RS256 —— 它根本没有公钥").isNull();
    }

    @Test
    @DisplayName("两侧的密钥长度下限必须是同一个数")
    void secretLengthFloorsAgree() {
        // 如果一边要求 32 字节、另一边要求 16，那么用一把 16 字节的密钥部署时
        // 签发方能起来、校验方起不来（或者反过来），排查起点会完全错。
        assertThat(AdminTokenVerifier.MIN_SECRET_BYTES).isEqualTo(32);
    }
}

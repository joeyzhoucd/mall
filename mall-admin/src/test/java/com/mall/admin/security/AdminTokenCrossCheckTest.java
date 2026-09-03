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
                new AdminProperties.Jwt(SECRET, 3600),
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

    @Test
    @DisplayName("两侧的密钥长度下限必须是同一个数")
    void secretLengthFloorsAgree() {
        // 如果一边要求 32 字节、另一边要求 16，那么用一把 16 字节的密钥部署时
        // 签发方能起来、校验方起不来（或者反过来），排查起点会完全错。
        assertThat(AdminTokenVerifier.MIN_SECRET_BYTES).isEqualTo(32);
    }
}

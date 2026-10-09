package com.mall.admin.security;

import com.mall.admin.config.AdminProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * 令牌的签发与校验（RS256）。
 * <p>
 * 用 Spring Security 自带的 Nimbus 封装，没有引第三方 JWT 库：编解码能力
 * spring-security-oauth2-jose 已经提供，版本由 spring-security-bom 统一管，
 * 少一个需要单独盯安全更新的依赖。
 * <p>
 * 和旧实现（renren-fast）的区别：旧的是"随机串 + sys_user_token 表"，每次请求查一次库；
 * 这里是无状态 JWT，网关后面多副本不需要共享会话。代价写清楚：
 * <b>登出无法让令牌立即失效</b>，只能等它过期。要做到"登出即失效"得加一个 Redis 黑名单，
 * 当前没做，所以有效期不宜设长（默认 12 小时）。
 *
 * <h3>为什么是 RS256</h3>
 * 原来是 HS256：签发和验签是同一把密钥。服务端「默认拒绝」要求每个服务都能验签，
 * 把 HS256 密钥发给每个服务，就等于每个服务都能签发可从公网经网关使用的管理端令牌。
 * RS256 下私钥只在这里，网关和各服务拿公钥（公钥由私钥推导，部署时对照 JWT_PUBLIC_KEY）。
 * 2026-10-07 起签 RS256，过渡期同时认旧 HS256 令牌；2026-10-09 旧令牌全部过期后，HS256 整条路径、
 * JWT_SECRET 和它的公开默认值一起删除。私钥现在必填：缺了启动即失败，不再有任何兜底密钥。
 */
@Component
public class JwtService {

    /** 用户 id 放在这个自定义 claim 里。sub 放用户名，便于日志直接读。 */
    private static final String CLAIM_USER_ID = "uid";

    private final JwtEncoder encoder;
    private final JwsHeader header;
    private final JwtDecoder decoder;
    private final RSAPublicKey publicKey;
    private final long expireSeconds;

    public JwtService(AdminProperties properties) {
        String pem = properties.jwt().privateKey();
        if (pem == null || pem.isBlank() || pem.contains("${")) {
            // 启动即失败。没有私钥就签不出令牌，与其让第一次登录才报一个费解的异常，不如启动时说清楚。
            throw new IllegalStateException(
                    "mall.admin.jwt.private-key 未配置（环境变量 JWT_PRIVATE_KEY，RS256 PKCS#8 私钥）。"
                    + "本地生成方法见 mall-admin 的 application.yml。");
        }
        RSAPrivateCrtKey priv = parsePrivateKey(pem);
        this.publicKey = derivePublicKey(priv);
        RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(priv).build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        this.header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        this.decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        this.expireSeconds = properties.jwt().expireSeconds();
    }

    /** PKCS#8 私钥：PEM（带不带 BEGIN/END 行都行）或单行 base64。格式不对启动即失败。 */
    static RSAPrivateCrtKey parsePrivateKey(String text) {
        String b64 = text.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s+", "");
        try {
            return (RSAPrivateCrtKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY 不是有效的 RSA PKCS#8 私钥", e);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateCrtKey priv) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(priv.getModulus(), priv.getPublicExponent()));
        } catch (Exception e) {
            throw new IllegalStateException("无法从私钥推导 RSA 公钥", e);
        }
    }

    /** 签发令牌。 */
    public String issue(Long userId, String username) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expireSeconds))
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expireSeconds() {
        return expireSeconds;
    }

    /** RS256 公钥的单行 base64（X.509 DER）。部署时对照 JWT_PUBLIC_KEY 用。 */
    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 校验并解析令牌。
     *
     * @return 解析出的登录用户；令牌缺失、签名不对、算法不是 RS256、已过期等一律返回 null（不抛异常）。
     *         调用方是过滤器，那里对"无效令牌"和"没带令牌"的处理是一样的——
     *         都当作未认证，交给后面的 AuthenticationEntryPoint 去回 code:401。
     */
    public LoginUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Jwt jwt = decoder.decode(token);
            Object uid = jwt.getClaim(CLAIM_USER_ID);
            Long userId = uid instanceof Number number ? number.longValue() : null;
            if (userId == null) {
                return null;
            }
            return new LoginUser(userId, jwt.getSubject());
        } catch (JwtException ex) {
            return null;
        }
    }

    /** 已认证的后台用户。 */
    public record LoginUser(Long userId, String username) {
    }
}

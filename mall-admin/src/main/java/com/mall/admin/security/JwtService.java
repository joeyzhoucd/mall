package com.mall.admin.security;

import com.mall.admin.config.AdminProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * 令牌的签发与校验。
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
 * <h3>RS256（2026-10-07）</h3>
 * 原来是 HS256：签发和验签是同一把密钥。服务端要做「默认拒绝」就得让每个服务都能验签，
 * 而把 HS256 密钥发给每个服务，等于每个服务都能签发可从公网经网关使用的管理端令牌。
 * 改成 RS256 后私钥只在这里，网关和各服务拿公钥（公钥由私钥推导，不用单独配置）。
 * <p>
 * <b>过渡期</b>：配了私钥就用 RS256 签发；校验同时接受 RS256 和旧的 HS256（{@code secret}），
 * 让切换前签发的令牌在过期前（最长 expireSeconds）仍然可用。Nimbus 的每个 decoder 只接受它
 * 配置的那一种算法，不存在算法混淆。收尾时删掉 HS256 这一半和 JWT_SECRET。
 * 没配私钥（本地开发）时行为和原来一样：HS256 签发、HS256 校验。
 */
@Component
public class JwtService {

    /** 用户 id 放在这个自定义 claim 里。sub 放用户名，便于日志直接读。 */
    private static final String CLAIM_USER_ID = "uid";

    /** HS256 要求密钥至少 256 位 = 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwsHeader header;
    private final JwtDecoder rsaDecoder;      // 没配私钥时为 null
    private final JwtDecoder legacyDecoder;   // HS256；收尾时删除
    private final RSAPublicKey publicKey;     // 没配私钥时为 null
    private final long expireSeconds;

    public JwtService(AdminProperties properties) {
        String secret = properties.jwt().secret();
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // 启动即失败，而不是等第一次登录时抛一个来自 Nimbus 内部的费解异常。
            // 密钥太短是配置错误，越早、越明确地报出来越好。
            throw new IllegalStateException(
                    "mall.admin.jwt.secret 至少需要 " + MIN_SECRET_BYTES + " 字节（HS256 的要求），"
                    + "当前只有 " + keyBytes.length + " 字节。请检查 JWT_SECRET 环境变量。");
        }
        SecretKey hmacKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.legacyDecoder = NimbusJwtDecoder.withSecretKey(hmacKey).macAlgorithm(MacAlgorithm.HS256).build();

        String pem = properties.jwt().privateKey();
        if (pem != null && !pem.isBlank() && !pem.contains("${")) {
            RSAPrivateCrtKey priv = parsePrivateKey(pem);
            this.publicKey = derivePublicKey(priv);
            RSAKey jwk = new RSAKey.Builder(publicKey).privateKey(priv).build();
            this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
            this.header = JwsHeader.with(SignatureAlgorithm.RS256).build();
            this.rsaDecoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
        } else {
            this.publicKey = null;
            this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(hmacKey));
            this.header = JwsHeader.with(MacAlgorithm.HS256).build();
            this.rsaDecoder = null;
        }
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

    /** RS256 公钥的单行 base64（X.509 DER），没配私钥时为 null。部署时对照 JWT_PUBLIC_KEY 用。 */
    public String publicKeyBase64() {
        return publicKey == null ? null : Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 校验并解析令牌。
     *
     * @return 解析出的登录用户；令牌缺失、签名不对、已过期等一律返回 null（不抛异常）。
     *         调用方是过滤器，那里对"无效令牌"和"没带令牌"的处理是一样的——
     *         都当作未认证，交给后面的 AuthenticationEntryPoint 去回 code:401。
     */
    public LoginUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Jwt jwt = rsaDecoder == null ? null : tryDecode(rsaDecoder, token);
        if (jwt == null) {
            jwt = tryDecode(legacyDecoder, token);
        }
        if (jwt == null) {
            return null;
        }
        Object uid = jwt.getClaim(CLAIM_USER_ID);
        Long userId = uid instanceof Number number ? number.longValue() : null;
        if (userId == null) {
            return null;
        }
        return new LoginUser(userId, jwt.getSubject());
    }

    private static Jwt tryDecode(JwtDecoder decoder, String token) {
        try {
            return decoder.decode(token);
        } catch (JwtException ex) {
            return null;
        }
    }

    /** 已认证的后台用户。 */
    public record LoginUser(Long userId, String username) {
    }
}

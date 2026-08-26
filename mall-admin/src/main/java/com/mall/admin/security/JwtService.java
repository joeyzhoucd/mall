package com.mall.admin.security;

import com.mall.admin.config.AdminProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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
 */
@Component
public class JwtService {

    /** 用户 id 放在这个自定义 claim 里。sub 放用户名，便于日志直接读。 */
    private static final String CLAIM_USER_ID = "uid";

    /** HS256 要求密钥至少 256 位 = 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
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
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.expireSeconds = properties.jwt().expireSeconds();
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
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long expireSeconds() {
        return expireSeconds;
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

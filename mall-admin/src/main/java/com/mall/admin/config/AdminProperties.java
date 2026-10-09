package com.mall.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台管理服务自己的配置。前缀 mall.admin.*，见 application.yml。
 *
 * @param jwt     令牌相关
 * @param captcha 验证码相关
 */
@ConfigurationProperties(prefix = "mall.admin")
public record AdminProperties(Jwt jwt, Captcha captcha) {

    /**
     * @param expireSeconds 有效期秒数。JWT 无状态，登出无法立即失效，所以不宜过长。
     * @param privateKey    RS256 签名私钥（PKCS#8，PEM 或单行 base64），必填 —— 缺了
     *                      {@link com.mall.admin.security.JwtService} 启动即失败。签名能力只留在 mall-admin，
     *                      网关和各服务只拿公钥验。2026-10-09 起 HS256 的 secret 字段连同它的公开默认值一起删除。
     */
    public record Jwt(long expireSeconds, String privateKey) {
    }

    /**
     * @param expireSeconds 验证码有效期秒数
     */
    public record Captcha(long expireSeconds) {
    }
}

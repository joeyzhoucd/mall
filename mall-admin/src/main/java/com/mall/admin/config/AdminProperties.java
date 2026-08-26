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
     * @param secret        HS256 的签名密钥。长度必须 &gt;= 32 字节，否则 Nimbus 会直接拒绝，
     *                      这一点在 {@link com.mall.admin.security.JwtService} 里有显式校验——
     *                      与其等运行时抛一个不知所云的异常，不如启动时就报清楚。
     * @param expireSeconds 有效期秒数。JWT 无状态，登出无法立即失效，所以不宜过长。
     */
    public record Jwt(String secret, long expireSeconds) {
    }

    /**
     * @param expireSeconds 验证码有效期秒数
     */
    public record Captcha(long expireSeconds) {
    }
}

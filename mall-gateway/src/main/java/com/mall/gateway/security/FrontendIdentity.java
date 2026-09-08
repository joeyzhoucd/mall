package com.mall.gateway.security;

/**
 * 网关从前台 Spring Session 中解析出的会员身份。
 */
public record FrontendIdentity(Long memberId, String username) {
}

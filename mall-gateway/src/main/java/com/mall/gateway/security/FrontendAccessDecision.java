package com.mall.gateway.security;

import org.springframework.http.HttpStatus;

/**
 * 前台入口安全检查结果。
 */
public record FrontendAccessDecision(boolean allowed, HttpStatus status, String message, FrontendIdentity identity) {

    public static FrontendAccessDecision allow(FrontendIdentity identity) {
        return new FrontendAccessDecision(true, null, null, identity);
    }

    public static FrontendAccessDecision reject(HttpStatus status, String message) {
        return new FrontendAccessDecision(false, status, message, null);
    }
}

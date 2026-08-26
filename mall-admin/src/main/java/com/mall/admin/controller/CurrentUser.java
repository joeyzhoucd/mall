package com.mall.admin.controller;

import com.mall.admin.security.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从安全上下文里取当前登录用户。
 * <p>
 * 单独抽出来是为了让"当前用户是谁"只有一个取法。散落在各 controller 里各写一遍
 * SecurityContextHolder 的强制类型转换，很容易某处写错类型而拿到 null。
 */
final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * @return 当前登录用户；理论上不会是 null（未认证的请求根本进不到 controller，
     *         会被 SecurityConfig 的 authenticationEntryPoint 挡掉），
     *         但仍然做了判空 —— 万一有人往白名单里加了新路径又忘了这一点，
     *         宁可拿到 null 走进正常的空值分支，也不要抛一个 ClassCastException。
     */
    static JwtService.LoginUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return authentication.getPrincipal() instanceof JwtService.LoginUser user ? user : null;
    }

    static Long userId() {
        JwtService.LoginUser user = get();
        return user == null ? null : user.userId();
    }
}

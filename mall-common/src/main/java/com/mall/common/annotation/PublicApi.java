package com.mall.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「公网可达、不需要管理端令牌」的接口：前台页面和它们的 AJAX、会员登录注册、
 * 带签名的支付回调、管理端的登录/验证码。可以标在方法或整个 controller 上。
 *
 * <p>为什么需要它（2026-10-07）：服务端对接口是<b>默认拒绝</b>的 ——
 * 既没有 {@link InternalApi} 也没有 {@code @PublicApi} 的接口，一律要求管理端 JWT
 * （{@link com.mall.common.security.AdminTokenInterceptor}）。之前只有网关在挡：
 * 集群里任何 pod 不带凭证就能调 product / order / ware 的后台接口
 * （实测 /order/order/list 直接返回 14 万多条订单，含收货信息）。
 * 默认拒绝的好处是<b>新加的接口天然受保护</b>，漏标的后果是 401 而不是裸奔。
 *
 * <p>和网关白名单必须一致：.github/scripts/check-public-api.js 在 CI 里双向比对 ——
 * 白名单放行了却没标（上线后前台 401）、标了却不在白名单（被意外当成公开接口），都会失败。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}

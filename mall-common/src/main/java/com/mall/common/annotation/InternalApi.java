package com.mall.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记「只给服务间（Feign）调用」的接口：请求必须带 {@code X-Internal-Token}，
 * 由 {@link com.mall.common.security.InternalTokenInterceptor} 校验。可以标在方法或整个 controller 上。
 *
 * <p>为什么需要它（缺陷 #5 第二层）：网关白名单只挡住了「经网关」的外部请求；
 * 集群里任何一个 pod 直接打 Service 地址、或者哪天白名单写错一条，这些接口
 * （搜索上下架、会员价写入、锁库存、会员登录……）都不验调用方。
 *
 * <p>为什么用注解而不是统一挪到 /internal/** 前缀：改路径要同时改 Feign 声明和 controller，
 * 两边滚动不同步时会有一段 404 窗口；注解不动任何路径。
 * 漏标靠 .github/scripts/check-internal-api.js 在 CI 里拦：每个 Feign 方法指向的接口必须带这个注解。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InternalApi {
}

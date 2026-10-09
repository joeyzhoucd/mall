package com.mall.common.security;

import com.mall.common.annotation.InternalApi;
import com.mall.common.annotation.PublicApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 服务端默认拒绝：既不是 {@link InternalApi} 也不是 {@link PublicApi} 的业务接口，
 * 必须带合法的管理端 JWT（{@code token} 头，网关的 AdminAuthFilter 已经验过一次并原样转发）。
 *
 * <p><b>只管 com.mall.* 的处理方法</b>。actuator、/error、springdoc 这些框架自带的
 * 处理方法也是 HandlerMethod，按包名排除比按路径枚举稳 —— 路径会被配置改掉，包名不会。
 * actuator 的公网暴露另由网关的 WebFilter 挡（见 actuator 那次修复）。
 *
 * <p>和 {@link InternalTokenInterceptor} 同样分两种模式，计数和日志两种模式完全一样：
 * report 只记录放行，enforce 返回 401。指标 {@code mall.admin.api.calls}
 * （Prometheus: mall_admin_api_calls_total），outcome = ok / missing / invalid / unconfigured。
 */
public class AdminTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "token";
    static final String METRIC = "mall.admin.api.calls";

    private static final Logger log = LoggerFactory.getLogger(AdminTokenInterceptor.class);

    private final AdminTokenVerifier verifier;
    private final boolean enforce;
    private final MeterRegistry registry;

    /**
     * @param publicKey 管理端 JWT 的 RS256 公钥（只验不签）。report 模式下允许为空（指标记 unconfigured）；
     *                  enforce 模式下为空则启动即失败；格式不对时 AdminTokenVerifier 也会启动即失败。
     *                  刻意不接受 HS256 密钥：那把密钥能签发令牌，不该发到各个服务。
     */
    public AdminTokenInterceptor(String publicKey, boolean enforce, MeterRegistry registry) {
        boolean configured = publicKey != null && !publicKey.isBlank() && !publicKey.contains("${");
        if (enforce && !configured) {
            throw new IllegalStateException("mall.admin-api.enforce=true 但 mall.admin.jwt.public-key 未配置（环境变量 JWT_PUBLIC_KEY）");
        }
        this.verifier = configured ? new AdminTokenVerifier(publicKey) : null;
        this.enforce = enforce;
        this.registry = registry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method) || !requiresAdmin(method)) {
            return true;
        }
        String outcome = outcome(request.getHeader(HEADER));
        String uri = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        Counter.builder(METRIC)
                .tag("outcome", outcome)
                .tag("uri", uri)
                .tag("mode", enforce ? "enforce" : "report")
                .register(registry)
                .increment();
        if ("ok".equals(outcome)) {
            return true;
        }
        log.warn("管理端接口校验未通过 outcome={} {} {} from={} mode={}",
                outcome, request.getMethod(), uri, request.getRemoteAddr(), enforce ? "enforce" : "report");
        if (!enforce) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"admin api requires a valid token\"}");
        return false;
    }

    static boolean requiresAdmin(HandlerMethod method) {
        Class<?> type = method.getBeanType();
        if (!type.getName().startsWith("com.mall.")) {
            return false;
        }
        return !(method.hasMethodAnnotation(InternalApi.class) || type.isAnnotationPresent(InternalApi.class)
                || method.hasMethodAnnotation(PublicApi.class) || type.isAnnotationPresent(PublicApi.class));
    }

    private String outcome(String token) {
        if (verifier == null) {
            return "unconfigured";
        }
        if (token == null || token.isBlank()) {
            return "missing";
        }
        return verifier.verify(token) != null ? "ok" : "invalid";
    }
}

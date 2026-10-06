package com.mall.common.security;

import com.mall.common.annotation.InternalApi;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 校验 {@link InternalApi} 接口上的 {@code X-Internal-Token}。
 *
 * <p><b>两种模式只差一件事</b>：校验不通过时，report 模式记一笔然后放行，enforce 模式返回 401。
 * 计数和日志两种模式完全一样 —— 所以 report 阶段攒下的数据，就是切 enforce 之后会被拦下的那些请求。
 * 分两步的原因：调用方和提供方的滚动顺序不受控，直接 enforce 的话，滚动途中旧版本调用方
 * 还没开始带令牌，Feign 调用会成片失败。
 *
 * <p>指标 {@code mall.internal.api.calls}（Prometheus: mall_internal_api_calls_total），
 * 标签 outcome = ok / missing / invalid / unconfigured，uri = 路由模板（不是原始路径，基数有界）。
 * ok 也计数：它是「Feign 确实带上了令牌」的正向证据，光看 missing=0 证明不了拦截器在工作。
 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Internal-Token";
    static final String METRIC = "mall.internal.api.calls";

    private static final Logger log = LoggerFactory.getLogger(InternalTokenInterceptor.class);

    private final byte[] token;
    private final boolean enforce;
    private final MeterRegistry registry;

    public InternalTokenInterceptor(String token, boolean enforce, MeterRegistry registry) {
        this.token = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
        this.enforce = enforce;
        this.registry = registry;
        if (enforce && this.token == null) {
            // 拦截模式下没有令牌 = 所有内部接口全拒，或者（更糟）有人想把它改成「没令牌就放行」。都不允许，启动即失败
            throw new IllegalStateException("mall.internal.enforce=true 但 mall.internal.token 未配置（环境变量 INTERNAL_TOKEN）");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod method) || !isInternal(method)) {
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
        // 不打印收到的令牌值；remoteAddr 用来定位是哪个 pod 没带
        log.warn("内部接口校验未通过 outcome={} {} {} from={} mode={}",
                outcome, request.getMethod(), uri, request.getRemoteAddr(), enforce ? "enforce" : "report");
        if (!enforce) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"internal api requires " + HEADER + "\"}");
        return false;
    }

    private static boolean isInternal(HandlerMethod method) {
        return method.hasMethodAnnotation(InternalApi.class)
                || method.getBeanType().isAnnotationPresent(InternalApi.class);
    }

    private String outcome(String supplied) {
        if (token == null) {
            return "unconfigured";
        }
        if (supplied == null || supplied.isEmpty()) {
            return "missing";
        }
        // 定长比较，不给按字节猜令牌留时间侧信道
        return MessageDigest.isEqual(token, supplied.getBytes(StandardCharsets.UTF_8)) ? "ok" : "invalid";
    }
}

package com.mall.gateway.filter;

import com.mall.gateway.security.FrontendAccessDecision;
import com.mall.gateway.security.FrontendIdentity;
import com.mall.gateway.security.FrontendSecurityProperties;
import com.mall.gateway.security.FrontendSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 前台统一入口安全：会话认证、黑白名单、IP/设备/会员维度限流。
 */
@Component
public class FrontendSecurityFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(FrontendSecurityFilter.class);

    public static final String MEMBER_ID_HEADER = "X-Member-Id";
    public static final String MEMBER_NAME_HEADER = "X-Member-Name";
    public static final String CLIENT_IP_HEADER = "X-Client-Ip";
    public static final String DEVICE_ID_HEADER = "X-Device-Id";

    private final FrontendSecurityService securityService;
    private final FrontendSecurityProperties properties;

    public FrontendSecurityFilter(FrontendSecurityService securityService, FrontendSecurityProperties properties) {
        this.securityService = securityService;
        this.properties = properties;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 150;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest original = exchange.getRequest();
        return securityService.check(exchange)
                .flatMap(decision -> {
                    if (!decision.allowed()) {
                        log.info("前台入口安全拦截: {} {} -> {}",
                                original.getMethod(), original.getURI().getPath(), decision.status());
                        return reject(exchange, decision);
                    }
                    ServerHttpRequest sanitized = stripTrustedHeaders(original);
                    ServerHttpRequest enriched = enrichTrustedHeaders(sanitized, original, decision.identity());
                    return chain.filter(exchange.mutate().request(enriched).build());
                });
    }

    private ServerHttpRequest stripTrustedHeaders(ServerHttpRequest request) {
        return request.mutate()
                .headers(headers -> {
                    headers.remove(MEMBER_ID_HEADER);
                    headers.remove(MEMBER_NAME_HEADER);
                    headers.remove(CLIENT_IP_HEADER);
                    headers.remove(DEVICE_ID_HEADER);
                })
                .build();
    }

    private ServerHttpRequest enrichTrustedHeaders(ServerHttpRequest request, ServerHttpRequest original,
                                                   FrontendIdentity identity) {
        String clientIp = clientIp(original);
        String deviceId = deviceId(original, clientIp);
        return request.mutate()
                .headers(headers -> {
                    headers.set(CLIENT_IP_HEADER, clientIp);
                    headers.set(DEVICE_ID_HEADER, deviceId);
                    if (identity != null) {
                        headers.set(MEMBER_ID_HEADER, String.valueOf(identity.memberId()));
                        if (identity.username() != null) {
                            headers.set(MEMBER_NAME_HEADER, identity.username());
                        }
                    }
                })
                .build();
    }

    private Mono<Void> reject(ServerWebExchange exchange, FrontendAccessDecision decision) {
        HttpStatus status = decision.status() == null ? HttpStatus.FORBIDDEN : decision.status();
        if (HttpStatus.UNAUTHORIZED.equals(status) && acceptsHtml(exchange.getRequest())) {
            exchange.getResponse().setStatusCode(HttpStatus.FOUND);
            exchange.getResponse().getHeaders().setLocation(java.net.URI.create(properties.getLoginPageUrl()));
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":" + status.value() + ",\"msg\":\"" + escapeJson(decision.message()) + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private static boolean acceptsHtml(ServerHttpRequest request) {
        List<MediaType> accepts = request.getHeaders().getAccept();
        return accepts.stream().anyMatch(type -> type.isCompatibleWith(MediaType.TEXT_HTML));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String clientIp(ServerHttpRequest request) {
        String forwarded = firstHeaderValue(request, "X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        String realIp = firstHeaderValue(request, "X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddress() == null || request.getRemoteAddress().getAddress() == null
                ? "unknown" : request.getRemoteAddress().getAddress().getHostAddress();
    }

    private static String deviceId(ServerHttpRequest request, String ip) {
        String header = firstHeaderValue(request, DEVICE_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return "fp-" + Integer.toHexString((ip + "|"
                + String.valueOf(firstHeaderValue(request, "User-Agent"))).hashCode());
    }

    private static String firstHeaderValue(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}

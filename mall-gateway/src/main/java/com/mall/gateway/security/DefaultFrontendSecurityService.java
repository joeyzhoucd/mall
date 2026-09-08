package com.mall.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class DefaultFrontendSecurityService implements FrontendSecurityService {

    private static final Logger log = LoggerFactory.getLogger(DefaultFrontendSecurityService.class);

    private static final String DEVICE_HEADER = "X-Device-Id";
    private static final String DEVICE_COOKIE = "MALL_DEVICE_ID";

    private final ReactiveStringRedisTemplate redis;
    private final FrontendSecurityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DefaultFrontendSecurityService(ReactiveStringRedisTemplate redis, FrontendSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public Mono<FrontendAccessDecision> check(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        if (!properties.isEnabled() || shouldSkip(path, request.getMethod())) {
            return Mono.just(FrontendAccessDecision.allow(null));
        }

        String ip = clientIp(request);
        String deviceId = deviceId(request, ip);
        if (matchesIp(ip, properties.getAccess().getDeniedIps())
                || containsIgnoreCase(properties.getAccess().getDeniedDeviceIds(), deviceId)) {
            return Mono.just(FrontendAccessDecision.reject(HttpStatus.FORBIDDEN, "访问被风控策略拒绝"));
        }

        boolean protectedPath = isProtectedPath(path);
        return resolveIdentity(request)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(identity -> checkAfterIdentity(identity.orElse(null), protectedPath, ip, deviceId));
    }

    private Mono<FrontendAccessDecision> checkAfterIdentity(FrontendIdentity identity, boolean protectedPath,
                                                            String ip, String deviceId) {
        if (protectedPath && identity == null) {
            return Mono.just(FrontendAccessDecision.reject(HttpStatus.UNAUTHORIZED, "请先登录"));
        }
        if (identity != null && properties.getAccess().getDeniedUserIds().contains(identity.memberId())) {
            return Mono.just(FrontendAccessDecision.reject(HttpStatus.FORBIDDEN, "访问被风控策略拒绝"));
        }
        if (isWhitelisted(ip, deviceId, identity) || !properties.getRateLimit().isEnabled()) {
            return Mono.just(FrontendAccessDecision.allow(identity));
        }
        return passesRateLimits(ip, deviceId, identity)
                .map(passed -> passed
                        ? FrontendAccessDecision.allow(identity)
                        : FrontendAccessDecision.reject(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));
    }

    private boolean shouldSkip(String path, HttpMethod method) {
        return HttpMethod.OPTIONS.equals(method)
                || path.startsWith("/api/")
                || path.startsWith("/actuator/")
                || isStaticAsset(path)
                || matchesPath(path, properties.getAccess().getBypassPathPatterns());
    }

    boolean isProtectedPath(String path) {
        return matchesPath(path, properties.getAccess().getProtectedPathPatterns());
    }

    private boolean matchesPath(String path, List<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        PathContainer parsedPath = PathContainer.parsePath(path);
        PathPatternParser parser = PathPatternParser.defaultInstance;
        for (String pattern : patterns) {
            if (StringUtils.hasText(pattern) && parser.parse(pattern).matches(parsedPath)) {
                return true;
            }
        }
        return false;
    }

    private Mono<FrontendIdentity> resolveIdentity(ServerHttpRequest request) {
        List<String> sessionIds = sessionIds(request);
        if (sessionIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(sessionIds)
                .concatMap(this::readIdentity)
                .next()
                .onErrorResume(ex -> {
                    log.warn("读取前台登录态失败，按未登录处理: {}", ex.toString());
                    return Mono.empty();
                });
    }

    private List<String> sessionIds(ServerHttpRequest request) {
        String raw = cookieValue(request, properties.getSession().getCookieName());
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        String normalized = unquote(raw.trim());
        List<String> ids = new ArrayList<>();
        addIfNew(ids, normalized);
        String urlDecoded = tryUrlDecode(normalized);
        if (urlDecoded != null) {
            addIfNew(ids, urlDecoded);
        }
        addIfNew(ids, tryDecodeBase64(normalized));
        addIfNew(ids, tryDecodeBase64Url(normalized));
        if (urlDecoded != null) {
            addIfNew(ids, tryDecodeBase64(urlDecoded));
            addIfNew(ids, tryDecodeBase64Url(urlDecoded));
        }
        return ids;
    }

    private Mono<FrontendIdentity> readIdentity(String sessionId) {
        String key = properties.getSession().getRedisKeyPrefix() + sessionId;
        return redis.opsForHash()
                .get(key, properties.getSession().getLoginAttribute())
                .cast(String.class)
                .flatMap(this::parseIdentity);
    }

    private Mono<FrontendIdentity> parseIdentity(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            Long id = node.has("id") && !node.path("id").isNull() ? node.path("id").asLong() : null;
            if (id == null) {
                return Mono.empty();
            }
            String username = node.has("username") && !node.path("username").isNull()
                    ? node.path("username").asString(null) : null;
            return Mono.just(new FrontendIdentity(id, username));
        } catch (Exception ex) {
            log.warn("前台登录态 JSON 解析失败，按未登录处理: {}", ex.toString());
            return Mono.empty();
        }
    }

    private Mono<Boolean> passesRateLimits(String ip, String deviceId, FrontendIdentity identity) {
        return consume("ip", ip, properties.getRateLimit().getIp())
                .flatMap(ok -> ok ? consume("device", deviceId, properties.getRateLimit().getDevice()) : Mono.just(false))
                .flatMap(ok -> {
                    if (!ok || identity == null) {
                        return Mono.just(ok);
                    }
                    return consume("user", String.valueOf(identity.memberId()), properties.getRateLimit().getUser());
                });
    }

    private Mono<Boolean> consume(String dimension, String value, FrontendSecurityProperties.Limit limit) {
        if (limit == null || !limit.isEnabled() || limit.getCapacity() <= 0 || limit.getWindow() == null
                || limit.getWindow().isZero() || limit.getWindow().isNegative()) {
            return Mono.just(true);
        }
        long windowSeconds = Math.max(1, limit.getWindow().toSeconds());
        long window = System.currentTimeMillis() / 1000 / windowSeconds;
        String key = properties.getRateLimit().getRedisKeyPrefix() + ":" + dimension + ":" + value + ":" + window;
        return redis.opsForValue().increment(key)
                .flatMap(count -> {
                    Mono<Boolean> allowed = Mono.just(count <= limit.getCapacity());
                    if (count == 1) {
                        return redis.expire(key, Duration.ofSeconds(windowSeconds + 5)).then(allowed);
                    }
                    return allowed;
                })
                .onErrorResume(ex -> {
                    log.warn("前台 {} 维度限流 Redis 失败，临时放行: {}", dimension, ex.toString());
                    return Mono.just(true);
                });
    }

    private boolean isWhitelisted(String ip, String deviceId, FrontendIdentity identity) {
        return matchesIp(ip, properties.getAccess().getAllowedIps())
                || containsIgnoreCase(properties.getAccess().getAllowedDeviceIds(), deviceId)
                || (identity != null && properties.getAccess().getAllowedUserIds().contains(identity.memberId()));
    }

    static String clientIp(ServerHttpRequest request) {
        String forwarded = firstHeaderValue(request, "X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String first = forwarded.split(",", 2)[0].trim();
            if (StringUtils.hasText(first) && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = firstHeaderValue(request, "X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }

    static String deviceId(ServerHttpRequest request, String ip) {
        String fromHeader = firstHeaderValue(request, DEVICE_HEADER);
        if (StringUtils.hasText(fromHeader)) {
            return normalizeDeviceId(fromHeader);
        }
        String fromCookie = cookieValue(request, DEVICE_COOKIE);
        if (StringUtils.hasText(fromCookie)) {
            return normalizeDeviceId(fromCookie);
        }
        String ua = Optional.ofNullable(firstHeaderValue(request, "User-Agent")).orElse("");
        return "fp-" + Integer.toHexString((ip + "|" + ua).hashCode());
    }

    private static String normalizeDeviceId(String value) {
        String trimmed = unquote(value.trim());
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    static boolean matchesIp(String ip, List<String> patterns) {
        if (!StringUtils.hasText(ip) || patterns == null) {
            return false;
        }
        for (String pattern : patterns) {
            if (!StringUtils.hasText(pattern)) {
                continue;
            }
            String p = pattern.trim();
            if (p.contains("/")) {
                if (matchesIpv4Cidr(ip, p)) {
                    return true;
                }
            } else if (ip.equals(p)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesIpv4Cidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/", 2);
            long address = ipv4ToLong(ip);
            long network = ipv4ToLong(parts[0]);
            int prefix = Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > 32) {
                return false;
            }
            long mask = prefix == 0 ? 0 : 0xffffffffL << (32 - prefix);
            return (address & mask) == (network & mask);
        } catch (Exception ex) {
            return false;
        }
    }

    private static long ipv4ToLong(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("not ipv4");
        }
        long result = 0;
        for (String part : parts) {
            int n = Integer.parseInt(part);
            if (n < 0 || n > 255) {
                throw new IllegalArgumentException("invalid ipv4 segment");
            }
            result = (result << 8) | n;
        }
        return result;
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        if (!StringUtils.hasText(needle) || values == null) {
            return false;
        }
        String normalized = needle.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(StringUtils::hasText)
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private static boolean isStaticAsset(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.endsWith(".css") || p.endsWith(".js") || p.endsWith(".png") || p.endsWith(".jpg")
                || p.endsWith(".jpeg") || p.endsWith(".gif") || p.endsWith(".ico") || p.endsWith(".svg")
                || p.endsWith(".woff") || p.endsWith(".woff2") || p.endsWith(".ttf") || p.endsWith(".map");
    }

    private static String firstHeaderValue(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static String cookieValue(ServerHttpRequest request, String name) {
        return request.getCookies().getFirst(name) == null ? null : request.getCookies().getFirst(name).getValue();
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String tryDecodeBase64(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String tryDecodeBase64Url(String value) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String tryUrlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void addIfNew(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }
}

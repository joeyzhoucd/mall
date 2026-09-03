package com.mall.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdminAuthFilter} 的行为约束。
 *
 * <h3>三类必须成立的事</h3>
 * <ol>
 *   <li><b>该拦的拦住</b> —— {@code /api/**} 无令牌/坏令牌一律 401；</li>
 *   <li><b>该放的放过</b> —— 前台各站点的路径、登录接口、验证码、CORS 预检。
 *       这一类比第一类更容易出事：漏放一条就是「后台登不上」或者「前台整站挂」；</li>
 *   <li><b>身份头不能被伪造</b> —— 网关会往下游加 {@code X-Admin-Id}，
 *       如果不先剥掉客户端传来的同名头，任何人都能自称管理员，
 *       等于把刚补上的门又拆了。</li>
 * </ol>
 */
class AdminAuthFilterTest {

    private static final String SECRET = "filter-test-secret-at-least-32-bytes!!";

    private final AdminAuthFilter filter = new AdminAuthFilter(SECRET);

    // ------------------------------------------------------------------ 工具

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String validToken() throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"admin\",\"uid\":9,\"exp\":"
                + (Instant.now().getEpochSecond() + 600) + "}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    /** 跑一次过滤器，返回「有没有被放行」以及放行时下游看到的请求。 */
    private record Result(boolean passed, ServerWebExchange downstream, HttpStatus status) { }

    private Result run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            seen.set(ex);
            return Mono.empty();
        }).block();
        return new Result(seen.get() != null, seen.get(),
                (HttpStatus) exchange.getResponse().getStatusCode());
    }

    // ------------------------------------------------------------------ 该放的放过

    @Test
    @DisplayName("非 /api 前缀完全不受影响（前台各站点、秒杀、结算都走这里）")
    void ignoresNonApiPaths() {
        for (String path : new String[]{
                "/coupon/seckill/address/mine", "/order/submit", "/cart.html",
                "/item/1.html", "/search/list.html", "/actuator/health"}) {
            Result r = run(MockServerHttpRequest.get(path).build());
            assertThat(r.passed()).as("路径 %s 被误拦了", path).isTrue();
        }
    }

    @Test
    @DisplayName("登录接口和验证码必须豁免（否则永远拿不到令牌，后台彻底登不上）")
    void exemptsLoginAndCaptcha() {
        assertThat(run(MockServerHttpRequest.post("/api/sys/login").build()).passed()).isTrue();
        assertThat(run(MockServerHttpRequest.get("/api/captcha.jpg").build()).passed()).isTrue();
    }

    @Test
    @DisplayName("CORS 预检必须放过（拦掉它浏览器会把真正的请求也判为失败）")
    void allowsPreflight() {
        Result r = run(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/api/coupon/coupon/list").build());
        assertThat(r.passed()).isTrue();
    }

    @Test
    @DisplayName("有效令牌放行，并把身份写进下游请求头")
    void passesWithValidToken() throws Exception {
        Result r = run(MockServerHttpRequest.get("/api/coupon/coupon/list")
                .header(AdminAuthFilter.TOKEN_HEADER, validToken()).build());

        assertThat(r.passed()).isTrue();
        assertThat(r.downstream().getRequest().getHeaders().getFirst(AdminAuthFilter.ADMIN_ID_HEADER))
                .isEqualTo("9");
        assertThat(r.downstream().getRequest().getHeaders().getFirst(AdminAuthFilter.ADMIN_NAME_HEADER))
                .isEqualTo("admin");
    }

    // ------------------------------------------------------------------ 该拦的拦住

    @Test
    @DisplayName("/api/** 无令牌 → 401，且不转发给下游")
    void rejectsMissingToken() {
        for (String path : new String[]{
                "/api/coupon/coupon/list",
                "/api/member/memberreceiveaddress/8000001/list",   // 实测过的越权路径
                "/api/product/spuinfo/list",
                "/api/ware/waresku/update",
                "/api/sys/user/list"}) {
            Result r = run(MockServerHttpRequest.get(path).build());
            assertThat(r.passed()).as("路径 %s 没被拦住", path).isFalse();
            assertThat(r.status()).as("路径 %s 的状态码", path).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("坏令牌 → 401（乱串、换密钥签的、过期的）")
    void rejectsBadTokens() throws Exception {
        String[] bad = {
                "garbage",
                "a.b.c",
                // 换一把密钥签的
                signWith("another-secret-long-enough-for-hs256-32b!", 600),
                // 过期的
                signWith(SECRET, -10),
        };
        for (String t : bad) {
            Result r = run(MockServerHttpRequest.get("/api/coupon/coupon/list")
                    .header(AdminAuthFilter.TOKEN_HEADER, t).build());
            assertThat(r.passed()).as("令牌 %s 被放过了", t).isFalse();
            assertThat(r.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private static String signWith(String secret, long offsetSeconds) throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"x\",\"uid\":1,\"exp\":"
                + (Instant.now().getEpochSecond() + offsetSeconds) + "}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    // ------------------------------------------------------------------ 身份头不可伪造

    @Test
    @DisplayName("客户端自带的 X-Admin-Id 必须被剥掉，不能借它冒充管理员")
    void stripsClientSuppliedIdentityHeaders() throws Exception {
        Result r = run(MockServerHttpRequest.get("/api/coupon/coupon/list")
                .header(AdminAuthFilter.TOKEN_HEADER, validToken())
                .header(AdminAuthFilter.ADMIN_ID_HEADER, "1")          // 伪造成超管
                .header(AdminAuthFilter.ADMIN_NAME_HEADER, "root")
                .build());

        assertThat(r.passed()).isTrue();
        // 必须是令牌里的 9 / admin，而不是客户端传的 1 / root
        assertThat(r.downstream().getRequest().getHeaders().get(AdminAuthFilter.ADMIN_ID_HEADER))
                .containsExactly("9");
        assertThat(r.downstream().getRequest().getHeaders().get(AdminAuthFilter.ADMIN_NAME_HEADER))
                .containsExactly("admin");
    }

    @Test
    @DisplayName("无令牌时伪造的身份头也不能混过去（连转发都不该发生）")
    void forgedHeadersDoNotBypassAuth() {
        Result r = run(MockServerHttpRequest.get("/api/coupon/coupon/list")
                .header(AdminAuthFilter.ADMIN_ID_HEADER, "1")
                .build());
        assertThat(r.passed()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ------------------------------------------------------------------ 构造期

    @Test
    @DisplayName("密钥过短时启动即失败")
    void failsFastOnShortSecret() {
        try {
            new AdminAuthFilter("short");
            assertThat(false).as("密钥过短却启动成功了").isTrue();
        } catch (IllegalStateException expected) {
            assertThat(expected).hasMessageContaining("JWT_SECRET");
        }
    }
}

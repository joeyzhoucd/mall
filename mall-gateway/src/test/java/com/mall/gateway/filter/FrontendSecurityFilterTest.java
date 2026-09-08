package com.mall.gateway.filter;

import com.mall.gateway.security.FrontendAccessDecision;
import com.mall.gateway.security.FrontendIdentity;
import com.mall.gateway.security.FrontendSecurityProperties;
import com.mall.gateway.security.FrontendSecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendSecurityFilterTest {

    private record Result(boolean passed, ServerWebExchange downstream, HttpStatus status, HttpHeaders headers) {
    }

    private Result run(MockServerHttpRequest request, FrontendAccessDecision decision) {
        FrontendSecurityService service = exchange -> Mono.just(decision);
        FrontendSecurityFilter filter = new FrontendSecurityFilter(service, new FrontendSecurityProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
        filter.filter(exchange, ex -> {
            seen.set(ex);
            return Mono.empty();
        }).block();
        return new Result(seen.get() != null, seen.get(),
                (HttpStatus) exchange.getResponse().getStatusCode(), exchange.getResponse().getHeaders());
    }

    @Test
    @DisplayName("放行时剥掉客户端伪造的会员头，再写入网关解析出的会员身份")
    void stripsForgedMemberHeadersAndAddsTrustedIdentity() {
        Result r = run(MockServerHttpRequest.get("/order/confirm.html")
                        .header(FrontendSecurityFilter.MEMBER_ID_HEADER, "1")
                        .header(FrontendSecurityFilter.MEMBER_NAME_HEADER, "root")
                        .header(FrontendSecurityFilter.DEVICE_ID_HEADER, "device-a")
                        .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                        .build(),
                FrontendAccessDecision.allow(new FrontendIdentity(8000001L, "lt0001")));

        assertThat(r.passed()).isTrue();
        HttpHeaders headers = r.downstream().getRequest().getHeaders();
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_ID_HEADER)).containsExactly("8000001");
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_NAME_HEADER)).containsExactly("lt0001");
        assertThat(headers.get(FrontendSecurityFilter.CLIENT_IP_HEADER)).containsExactly("203.0.113.10");
        assertThat(headers.get(FrontendSecurityFilter.DEVICE_ID_HEADER)).containsExactly("device-a");
    }

    /**
     * <b>匿名放行时也必须剥掉伪造的会员头。</b>
     *
     * <h3>为什么单独加这一条：上面那条测试保护不了剥离逻辑</h3>
     * 上面 stripsForgedMemberHeadersAndAddsTrustedIdentity 测的是<b>已登录</b>的情况，
     * 此时 enrichTrustedHeaders 会用 {@code headers.set(...)} 覆盖会员头 ——
     * 所以剥不剥都会被顶掉，断言两种情况下都通过。
     * <p>
     * 实测确认过这个盲区：把 {@code stripTrustedHeaders(original)} 换成
     * {@code original}（即完全不剥），那条测试<b>照常通过</b>。
     *
     * <h3>而匿名路径是真的会漏</h3>
     * {@code identity == null} 时 enrich <b>不设</b> MEMBER_ID / MEMBER_NAME，
     * 于是不剥的话客户端伪造的 {@code X-Member-Id: 1} 会原样传到后端 ——
     * 而后端服务把这个头当成可信的会员身份。
     * 这是身份伪造，而且首页、搜索、商品详情这些匿名可访问的路径都会走到这里。
     * <p>
     * 顺带覆盖 username 为 null 的子情况：那时 MEMBER_NAME 也不会被 set。
     */
    @Test
    @DisplayName("匿名放行时也要剥掉伪造的会员头 —— 否则后端会把它当成可信身份")
    void stripsForgedMemberHeadersEvenWhenAnonymous() {
        Result r = run(MockServerHttpRequest.get("/1001.html")
                        .header(FrontendSecurityFilter.MEMBER_ID_HEADER, "1")
                        .header(FrontendSecurityFilter.MEMBER_NAME_HEADER, "root")
                        .build(),
                // 匿名但允许访问 —— 首页/搜索/商品详情就是这个状态
                FrontendAccessDecision.allow(null));

        assertThat(r.passed()).isTrue();
        HttpHeaders headers = r.downstream().getRequest().getHeaders();
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_ID_HEADER))
                .as("匿名请求把伪造的 X-Member-Id 透传给了后端 —— 后端会把它当成可信会员身份")
                .isNull();
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_NAME_HEADER))
                .as("匿名请求把伪造的 X-Member-Name 透传给了后端")
                .isNull();
    }

    /**
     * 已登录但 username 为 null 时，伪造的 MEMBER_NAME 同样不能透传。
     *
     * <p>enrich 里 MEMBER_NAME 的 set 是包在 {@code if (identity.username() != null)} 里的，
     * 所以这一条不靠剥离就会漏 —— 而"会员没有用户名"在这个库里是真实存在的
     * （ums_member 里有昵称为空的行）。
     */
    @Test
    @DisplayName("已登录但没有用户名时，伪造的 X-Member-Name 也不能透传")
    void stripsForgedMemberNameWhenIdentityHasNoUsername() {
        Result r = run(MockServerHttpRequest.get("/order/confirm.html")
                        .header(FrontendSecurityFilter.MEMBER_NAME_HEADER, "root")
                        .build(),
                FrontendAccessDecision.allow(new FrontendIdentity(8000001L, null)));

        assertThat(r.passed()).isTrue();
        HttpHeaders headers = r.downstream().getRequest().getHeaders();
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_ID_HEADER)).containsExactly("8000001");
        assertThat(headers.get(FrontendSecurityFilter.MEMBER_NAME_HEADER))
                .as("username 为 null 时 enrich 不会 set，伪造值只能靠剥离拦住")
                .isNull();
    }

    @Test
    @DisplayName("未登录访问 HTML 页面时跳转登录页")
    void redirectsHtmlRequestsToLogin() {
        Result r = run(MockServerHttpRequest.get("/order/confirm.html")
                        .accept(MediaType.TEXT_HTML)
                        .build(),
                FrontendAccessDecision.reject(HttpStatus.UNAUTHORIZED, "请先登录"));

        assertThat(r.passed()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.FOUND);
        assertThat(r.headers().getLocation()).hasToString("http://auth.mall.com/login.html");
    }

    @Test
    @DisplayName("未登录访问接口时返回 401 JSON")
    void returnsJsonForApiLikeFrontendRequests() {
        Result r = run(MockServerHttpRequest.post("/coupon/seckill/grab/1").build(),
                FrontendAccessDecision.reject(HttpStatus.UNAUTHORIZED, "请先登录"));

        assertThat(r.passed()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("黑名单命中返回 403")
    void returnsForbidden() {
        Result r = run(MockServerHttpRequest.get("/").build(),
                FrontendAccessDecision.reject(HttpStatus.FORBIDDEN, "访问被风控策略拒绝"));

        assertThat(r.passed()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("限流命中返回 429")
    void returnsTooManyRequests() {
        Result r = run(MockServerHttpRequest.get("/").build(),
                FrontendAccessDecision.reject(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"));

        assertThat(r.passed()).isFalse();
        assertThat(r.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}

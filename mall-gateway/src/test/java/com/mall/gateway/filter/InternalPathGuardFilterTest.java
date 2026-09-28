package com.mall.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalPathGuardFilterTest {

    /**
     * 走真实的 filter：放行返回 empty，拦下返回状态码。
     * URI 要带上 scheme + host，和 Reactor Netty 的做法一样（scheme://Host头 + 请求行）——
     * 单写 {@code URI.create("//order//internal")} 会把 order 解析成<b>主机名</b>、路径变成 //internal，
     * 第一版就这么测错过。
     */
    private static Optional<HttpStatus> run(String rawPath) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("http://cart.mall.com" + rawPath)).build());
        AtomicBoolean passed = new AtomicBoolean(false);
        new InternalPathGuardFilter().filter(ex, e -> {
            passed.set(true);
            return Mono.empty();
        }).block();
        return passed.get() ? Optional.empty()
                : Optional.ofNullable(ex.getResponse().getStatusCode()).map(s -> HttpStatus.valueOf(s.value()));
    }

    @Test
    void internalPathsAreHiddenBehind404() {
        for (String uri : List.of(
                "/order/internal/reco/complements/1",
                "/api/order/internal/reco/complements/1",   // 后台路由会改写成 /order/internal/...
                "/order/internal")) {
            assertThat(run(uri)).as(uri).contains(HttpStatus.NOT_FOUND);
        }
    }

    /** 每一种都是「朴素 startsWith 会漏、后端仍会匹配到」的写法 */
    @Test
    void pathTricksDoNotSlipThrough() {
        for (String uri : List.of(
                "//order//internal/reco/complements/1",
                "/order/./internal/reco/complements/1",
                "/order/x/../internal/reco/complements/1",
                "/order/internal;jsessionid=abc/reco/complements/1",
                "/order;v=1/internal/reco/complements/1",
                "/order%2Finternal/reco/complements/1",
                "/ORDER/Internal/reco/complements/1")) {
            assertThat(run(uri)).as(uri).contains(HttpStatus.NOT_FOUND);
        }
    }

    /** 挡过头同样是故障：下单、支付回调这些路径必须照常放行 */
    @Test
    void ordinaryOrderPathsPassThrough() {
        for (String uri : List.of(
                "/order/toTrade",
                "/order/payments/notify",
                "/api/order/order/list",
                "/order/internals/x",
                "/order/internalx",
                "/internal/x",
                "/")) {
            assertThat(run(uri)).as(uri).isEmpty();
        }
    }
}

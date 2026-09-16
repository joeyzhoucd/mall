package com.mall.gateway.filter;

import com.mall.gateway.security.FrontendSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守一个<b>实测存在过的漏洞</b>：2026-09-16 之前
 * {@code http://mall.com/actuator/health}、{@code /actuator/prometheus}、
 * {@code /actuator} 从<b>公网无鉴权</b>就能打到、全部返回 200，
 * health 里带着 Consul leader 的内网 IP 和 config server 的文件路径。
 *
 * <p><b>这道规则的两个方向都会出事，而且后果不对称：</b>
 * <ul>
 *   <li>挡不住 → 内网拓扑和全部指标对公网敞开</li>
 *   <li><b>挡过头 → Prometheus 抓不到指标，而且是静默的</b>：面板变空、
 *       告警不响，看起来像「系统很安静」。这一种更难发现。</li>
 * </ul>
 */
class ManagementEndpointGuardFilterTest {

    /** @return 被挡时返回状态码；放行时返回 empty */
    private java.util.Optional<HttpStatus> run(MockServerHttpRequest request) {
        return run(request, new FrontendSecurityProperties());
    }

    private java.util.Optional<HttpStatus> run(MockServerHttpRequest request, FrontendSecurityProperties props) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean passed = new AtomicBoolean(false);
        new ManagementEndpointGuardFilter(props)
                .filter(exchange, ex -> {
                    passed.set(true);
                    return Mono.empty();
                })
                .block();
        return passed.get()
                ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(exchange.getResponse().getStatusCode())
                        .map(s -> HttpStatus.valueOf(s.value()));
    }

    @Test
    @DisplayName("从域名进来的 /actuator/** 一律 404")
    void blockedForExternalHosts() {
        for (String host : List.of("mall.com", "seckill.mall.com", "item.mall.com", "admin.mall.com")) {
            assertThat(run(MockServerHttpRequest.get("/actuator/health").header("Host", host).build()))
                    .as("Host=%s 的 /actuator/health 必须被挡", host)
                    .contains(HttpStatus.NOT_FOUND);
        }
        assertThat(run(MockServerHttpRequest.get("/actuator/prometheus").header("Host", "mall.com").build()))
                .as("/actuator/prometheus 从公网可读等于把全部指标送出去")
                .contains(HttpStatus.NOT_FOUND);
        assertThat(run(MockServerHttpRequest.get("/actuator").header("Host", "mall.com").build()))
                .as("端点索引页 /actuator 本身也要挡，它会列出所有可用端点")
                .contains(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("直连 pod（Host 是 IP）的 /actuator/** 必须放行，否则监控静默变哑")
    void allowedForDirectPodAccess() {
        // 192.168.99.194:88 是实测中 Prometheus 抓网关时用的 Host 值
        for (String host : List.of(
                "192.168.99.194:88", "192.168.99.194", "10.42.0.7:10000", "localhost:8080", "[::1]:88")) {
            assertThat(run(MockServerHttpRequest.get("/actuator/prometheus").header("Host", host).build()))
                    .as("Host=%s 是集群内直连 pod，挡掉它会让 Prometheus 静默抓不到指标", host)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("负控：非 actuator 路径不受这道规则影响")
    void nonActuatorPathsUnaffected() {
        // 没有这一条的话，一个"把所有域名请求都挡掉"的实现也能让上面两条通过。
        assertThat(run(MockServerHttpRequest.get("/promotion.html").header("Host", "mall.com").build()))
                .isEmpty();
        assertThat(run(MockServerHttpRequest.get("/order/confirm.html").header("Host", "cart.mall.com").build()))
                .isEmpty();
        // 前缀相近但不是 actuator 的路径不能误伤
        assertThat(run(MockServerHttpRequest.get("/actuatorfoo").header("Host", "mall.com").build()))
                .isEmpty();
    }

    @Test
    @DisplayName("判断不了来源时偏保守：没有 Host 头就挡掉")
    void missingHostIsBlocked() {
        assertThat(run(MockServerHttpRequest.get("/actuator/health").build()))
                .contains(HttpStatus.NOT_FOUND);
    }

    /**
     * 开关必须独立于 {@code properties.enabled}：把整套前台风控关掉是运维动作，
     * 不该顺带把 actuator 重新暴露到公网上。
     */
    @Test
    @DisplayName("关掉前台风控总开关，actuator 仍然要被挡")
    void stillBlockedWhenFrontendSecurityDisabled() {
        FrontendSecurityProperties props = new FrontendSecurityProperties();
        props.setEnabled(false);

        assertThat(run(MockServerHttpRequest.get("/actuator/health").header("Host", "mall.com").build(), props))
                .contains(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("负控：显式关掉这道规则时放行（证明断言不是恒为真）")
    void canBeDisabledExplicitly() {
        FrontendSecurityProperties props = new FrontendSecurityProperties();
        props.getAccess().setBlockManagementEndpoints(false);

        assertThat(run(MockServerHttpRequest.get("/actuator/health").header("Host", "mall.com").build(), props))
                .isEmpty();
    }
}

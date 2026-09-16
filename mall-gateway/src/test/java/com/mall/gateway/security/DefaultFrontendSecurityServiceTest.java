package com.mall.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFrontendSecurityServiceTest {

    /**
     * 这一组守的是一个<b>实测存在过的漏洞</b>：2026-09-16 之前
     * {@code http://mall.com/actuator/health} 和 {@code /actuator/prometheus}
     * 从公网无鉴权就能打到、返回 200，health 里还带着 Consul leader 的内网 IP
     * 和 config server 的文件路径。
     * <p>
     * 而它<b>必须只挡外部</b>：Prometheus 是直连 pod
     * （Host 头是 IP，实测 {@code 192.168.99.194:88}）抓指标的，
     * 挡错了整套监控会一起哑掉 —— 而且是静默哑掉。
     */
    private java.util.Optional<org.springframework.http.HttpStatus> reject(MockServerHttpRequest request) {
        FrontendSecurityProperties props = new FrontendSecurityProperties();
        // 【为什么要关掉限流】没被 actuator 规则挡下的请求会继续走到限流那一步，
        // 而限流要用 Redis（这里传的是 null）。关掉它，负控才能走完整条链路。
        // 顺带一提：第一版没关，负控直接 NPE —— 那次失败本身就说明
        // actuator 这道拦截确实在 Redis 之前就短路了。
        props.getRateLimit().setEnabled(false);
        DefaultFrontendSecurityService service =
                new DefaultFrontendSecurityService(null, props);
        FrontendAccessDecision d = service.check(
                org.springframework.mock.web.server.MockServerWebExchange.from(request)).block();
        return d != null && !d.allowed()
                ? java.util.Optional.of(d.status())
                : java.util.Optional.empty();
    }

    @Test
    @DisplayName("从域名进来的 /actuator/** 一律 404")
    void actuatorBlockedForExternalHosts() {
        for (String host : java.util.List.of(
                "mall.com", "seckill.mall.com", "item.mall.com", "admin.mall.com")) {
            assertThat(reject(MockServerHttpRequest.get("/actuator/health").header("Host", host).build()))
                    .as("Host=%s 的 /actuator/health 必须被挡", host)
                    .contains(org.springframework.http.HttpStatus.NOT_FOUND);
        }
        assertThat(reject(MockServerHttpRequest.get("/actuator/prometheus").header("Host", "mall.com").build()))
                .as("/actuator/prometheus 从公网可读等于把全部指标送出去")
                .contains(org.springframework.http.HttpStatus.NOT_FOUND);
        assertThat(reject(MockServerHttpRequest.get("/actuator").header("Host", "mall.com").build()))
                .as("端点索引页 /actuator 本身也要挡，它会列出所有可用端点")
                .contains(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    /**
     * <b>这一条比上一条更容易出事。</b>挡过头的后果是 Prometheus 抓不到任何指标，
     * 而抓不到指标是<b>静默</b>的 —— 面板变空、告警不响，看起来像"系统很安静"。
     */
    @Test
    @DisplayName("直连 pod（Host 是 IP）的 /actuator/** 必须放行，否则监控全哑")
    void actuatorAllowedForDirectPodAccess() {
        for (String host : java.util.List.of(
                "192.168.99.194:88", "192.168.99.194", "10.42.0.7:10000", "localhost:8080", "[::1]:88")) {
            assertThat(reject(MockServerHttpRequest.get("/actuator/prometheus").header("Host", host).build()))
                    .as("Host=%s 是集群内直连 pod，挡掉它会让 Prometheus 静默抓不到指标", host)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("负控：非 actuator 路径不受这道规则影响")
    void nonActuatorPathsUnaffected() {
        // 没有这一条的话，一个"把所有域名请求都挡掉"的实现也能让上面两条通过。
        assertThat(reject(MockServerHttpRequest.get("/promotion.html").header("Host", "mall.com").build()))
                .isEmpty();
        // 前缀相近但不是 actuator 的路径不能误伤
        assertThat(reject(MockServerHttpRequest.get("/actuatorfoo").header("Host", "mall.com").build()))
                .isEmpty();
    }

    @Test
    @DisplayName("判断不了来源时偏保守：没有 Host 头就挡掉")
    void missingHostIsBlocked() {
        assertThat(reject(MockServerHttpRequest.get("/actuator/health").build()))
                .contains(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("前台保护路径只覆盖需要会员身份的交易入口")
    void protectedPathDefaults() {
        DefaultFrontendSecurityService service = new DefaultFrontendSecurityService(null, new FrontendSecurityProperties());

        assertThat(service.isProtectedPath("/order/confirm.html")).isTrue();
        assertThat(service.isProtectedPath("/coupon/seckill/grab/1")).isTrue();
        assertThat(service.isProtectedPath("/coupon/seckill/message/10/address")).isTrue();
        assertThat(service.isProtectedPath("/coupon/seckill/address/mine")).isTrue();

        assertThat(service.isProtectedPath("/")).isFalse();
        assertThat(service.isProtectedPath("/search/list.html")).isFalse();
        assertThat(service.isProtectedPath("/item/1.html")).isFalse();
        assertThat(service.isProtectedPath("/cart.html")).isFalse();
    }

    @Test
    @DisplayName("客户端 IP 取 X-Forwarded-For 第一段")
    void clientIpUsesFirstForwardedValue() {
        String ip = DefaultFrontendSecurityService.clientIp(MockServerHttpRequest.get("/")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                .build());

        assertThat(ip).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("IP 黑白名单支持 IPv4 CIDR")
    void ipMatcherSupportsCidr() {
        assertThat(DefaultFrontendSecurityService.matchesIp("203.0.113.10", java.util.List.of("203.0.113.0/24")))
                .isTrue();
        assertThat(DefaultFrontendSecurityService.matchesIp("203.0.114.10", java.util.List.of("203.0.113.0/24")))
                .isFalse();
    }

    @Test
    @DisplayName("设备维度优先使用 X-Device-Id")
    void deviceIdUsesHeader() {
        String deviceId = DefaultFrontendSecurityService.deviceId(MockServerHttpRequest.get("/")
                .header("X-Device-Id", "device-a")
                .header("User-Agent", "browser")
                .build(), "203.0.113.10");

        assertThat(deviceId).isEqualTo("device-a");
    }
}

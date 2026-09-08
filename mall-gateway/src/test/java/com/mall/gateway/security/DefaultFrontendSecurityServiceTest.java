package com.mall.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultFrontendSecurityServiceTest {

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

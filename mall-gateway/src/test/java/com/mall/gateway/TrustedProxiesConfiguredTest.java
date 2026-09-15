package com.mall.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住 {@code trusted-proxies} —— 少了它，下单成功后会跳到 pod 内网 IP。
 *
 * <h3>对应的真实故障（2026-09-15 修）</h3>
 * Spring Cloud Gateway 5.x 的安全加固：{@code X-Forwarded-*} 是客户端可伪造的头，
 * 只有对端地址匹配 {@code trusted-proxies} 时才采信。<b>不配就一律不可信</b>，
 * 网关会把进来的 {@code X-Forwarded-*} 全部删掉、自己也不追加。
 * <p>
 * 后果：mall-order 的 {@code externalBase()} 读不到 {@code X-Forwarded-Host}，
 * 回退到 {@code Host}，而 Netty 客户端已经把 Host 换成了 pod 地址，
 * 于是提交订单后跳转到 {@code http://192.168.48.9:9000/order/payment.html} ——
 * 订单真的创建了，但用户看到的是一个打不开的页面。
 *
 * <h3>为什么要专门测</h3>
 * 同模块的 {@link ConfigMetadataTest} 能发现"属性名写错/失效"，
 * 但发现不了"这一行被整个删掉"——而删掉它同样是静默的：
 * 网关照常启动、健康检查全绿、所有页面都能打开，
 * <b>只有走到下单最后一步才会暴露</b>。
 * <p>
 * 所以这里不只断言"这一行在"，还断言<b>这个正则真的匹配 ingress 的 Pod IP</b>——
 * 写了一个匹配不上的正则，效果和没写完全一样。
 */
class TrustedProxiesConfiguredTest {

    private static final String KEY = "spring.cloud.gateway.server.webflux.trusted-proxies";

    /** ingress-nginx 控制器的 Pod IP（实测 192.168.48.54），集群 --cluster-cidr=192.168.0.0/16 */
    private static final String INGRESS_POD_IP = "192.168.48.54";

    private String trustedProxies() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            Object value = source.getProperty(KEY);
            if (value != null) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    @Test
    @DisplayName("trusted-proxies 必须配着 —— 没有它，下游拿不到 X-Forwarded-*")
    void trustedProxiesIsConfigured() throws Exception {
        String value = trustedProxies();
        assertNotNull(value, KEY + " 不见了。没有它，网关会删掉所有 X-Forwarded-* 头，"
                + "mall-order 的跳转会指向 pod 内网 IP，下单最后一步的页面打不开");
        assertFalse(value.isBlank(), KEY + " 是空的，等于没配");
    }

    /**
     * <b>本文件的核心断言。</b>
     * 光有这一行不够 —— 正则匹配不上 ingress 的 Pod IP 的话，行为和没配一模一样，
     * 而且同样不会有任何报错。
     */
    @Test
    @DisplayName("这个正则必须真的匹配 ingress-nginx 的 Pod IP")
    void regexActuallyMatchesTheIngressPodIp() throws Exception {
        Pattern pattern = Pattern.compile(trustedProxies());

        assertTrue(pattern.matcher(INGRESS_POD_IP).matches(),
                "trusted-proxies 匹配不上 ingress 的 Pod IP " + INGRESS_POD_IP
                        + "，网关仍然会把它当成不可信来源、删掉 X-Forwarded-*。"
                        + "集群 Pod 网段是 192.168.0.0/16");
    }

    /**
     * 负控：不能图省事写成 {@code .*}。
     * 那样连集群外直连网关的请求都会被采信，X-Forwarded-For 可以被任意伪造，
     * 而 FrontendSecurityFilter 的 IP 维度限流正是按客户端 IP 算的。
     */
    @Test
    @DisplayName("负控：不能宽到把集群外地址也当成可信代理")
    void regexMustNotTrustEverything() throws Exception {
        Pattern pattern = Pattern.compile(trustedProxies());

        assertFalse(pattern.matcher("203.0.113.7").matches(),
                "trusted-proxies 把公网地址也当成可信代理了 —— "
                        + "那样任何能直连网关的来源都能伪造 X-Forwarded-For 绕过 IP 限流");
        assertFalse(pattern.matcher("10.244.0.1").matches(),
                "范围过宽：只应信任本集群的 Pod 网段 192.168.0.0/16");
    }
}

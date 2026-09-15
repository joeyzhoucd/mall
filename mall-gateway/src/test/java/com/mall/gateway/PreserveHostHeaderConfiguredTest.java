package com.mall.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住 {@code PreserveHostHeader} —— 少了它，下单成功后会跳到 pod 内网 IP。
 *
 * <h3>对应的真实故障（2026-09-15）</h3>
 * 网关默认会把转发给下游的 {@code Host} 头换成解析出来的下游地址
 * （比如 {@code 192.168.48.9:9000}）。而 mall-order 的
 * {@code OrderWebController.externalBase()} 要拼一个<b>浏览器能打开的</b>
 * 绝对地址做 302 跳转，它先读 {@code X-Forwarded-Host}，读不到就回退到 {@code Host}。
 * <p>
 * X-Forwarded-Host 被网关的安全加固删掉了（见 application.yml 里的长注释），
 * 于是回退到 Host，拿到 pod 地址，跳转指向
 * {@code http://192.168.48.9:9000/order/payment.html} ——
 * <b>订单真的创建了</b>，用户看到的却是打不开的页面。
 *
 * <h3>为什么要专门测</h3>
 * 这一行被删掉是<b>完全静默</b>的：网关照常启动、健康检查全绿、
 * 所有页面都打得开，只有走到下单最后一步、而且要看跳转到<b>哪里</b>才会发现。
 * 只断言"下单成功"是发现不了的 —— 实测时那一条就是绿的。
 * <p>
 * 端到端那一层由 {@code mall-deploy/e2e/storefront-flow.sh} 守（它会断言
 * 跳转地址不是内网 IP）；这里守的是配置本身，跑得起来不需要集群。
 */
class PreserveHostHeaderConfiguredTest {

    private static final String KEY_PREFIX = "spring.cloud.gateway.server.webflux.default-filters";

    /** 读出 default-filters 列表（YamlPropertySourceLoader 会展开成 xxx[0]、xxx[1]…） */
    private List<String> defaultFilters() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        List<String> filters = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            for (int i = 0; i < 50; i++) {
                Object v = source.getProperty(KEY_PREFIX + "[" + i + "]");
                if (v == null) {
                    break;
                }
                filters.add(String.valueOf(v));
            }
        }
        return filters;
    }

    /**
     * <b>本文件的核心断言。</b>
     */
    @Test
    @DisplayName("default-filters 里必须有 PreserveHostHeader")
    void preserveHostHeaderIsADefaultFilter() throws Exception {
        List<String> filters = defaultFilters();

        assertFalse(filters.isEmpty(),
                KEY_PREFIX + " 是空的。没有 PreserveHostHeader，网关会把 Host 换成下游 pod 地址，"
                        + "mall-order 的跳转会指向 pod 内网 IP，下单最后一步的页面打不开");
        assertTrue(filters.contains("PreserveHostHeader"),
                "default-filters 里没有 PreserveHostHeader，只有 " + filters
                        + "。它是【全局】过滤器，不能只加在某一条路由上 —— "
                        + "/order/** 和各个 Host 路由都要靠它");
    }

    /**
     * 这个键的前缀必须是 {@code spring.cloud.gateway.server.webflux.*}。
     * <p>
     * Spring Cloud 2025.x 把网关拆成了 server-webflux / server-webmvc 等变体，
     * 配置前缀跟着加了两层。写旧前缀<b>不报错也不告警</b>，那段配置直接不生效 ——
     * 这个项目已经在 routes 上栽过一次（网关一条路由都没有，所有 /api/** 全 404，
     * 而 pod 是 Ready、健康检查是 UP）。
     * <p>
     * 注意：同模块的 {@link ConfigMetadataTest} 会用依赖自带的元数据校验
     * "这个键存不存在"，和这里是互补的 —— 它管拼写，这里管"有没有被删掉"。
     */
    @Test
    @DisplayName("负控：旧前缀 spring.cloud.gateway.default-filters 不该被用")
    void mustNotUseTheLegacyPrefix() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            assertFalse(source.containsProperty("spring.cloud.gateway.default-filters[0]"),
                    "用了旧前缀 spring.cloud.gateway.default-filters —— "
                            + "Spring Cloud 2025.x 已经把它挪到 server.webflux 下面，"
                            + "旧名字不报错、也不生效");
        }
    }
}

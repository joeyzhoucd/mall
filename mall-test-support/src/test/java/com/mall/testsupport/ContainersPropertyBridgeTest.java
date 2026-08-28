package com.mall.testsupport;

import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 守住容器和应用之间那层「属性桥」注册了正确的键。
 *
 * <h3>为什么值得单独测</h3>
 * 这层桥存在的原因是 {@code @ServiceConnection} <b>只提供 ConnectionDetails bean，
 * 不往 Environment 写属性</b>，而这个项目里有几处客户端是绕开 Boot 自动配置、
 * 直接 {@code @Value} 读原始属性自己建的（RedissonConfig、ElasticSearchConfig）。
 * 桥写错一个键名，结果不是报错，而是<b>应用安静地连到 application.yml 里的默认地址</b> ——
 * 表现成一个和键名毫无关系的连接超时。
 *
 * <h3>为什么不用起 Spring 上下文</h3>
 * 起上下文就要有 Docker，而本机没有；那样这条测试会变成"只能在 CI 跑"，
 * 而它要守的恰恰是"一轮 CI 25 分钟才发现键名写错了"这种浪费。
 * 这里只验注册了哪些键 —— 用假的 registry 收集，容器用 mock（不启动、也不调用）。
 */
class ContainersPropertyBridgeTest {

    /** 只收集键名，不求值 —— 求值会去碰没启动的容器。 */
    private static Map<String, Supplier<Object>> capture(java.util.function.Consumer<DynamicPropertyRegistry> block) {
        Map<String, Supplier<Object>> captured = new LinkedHashMap<>();
        block.accept(new DynamicPropertyRegistry() {
            @Override
            public void add(String name, Supplier<Object> valueSupplier) {
                captured.put(name, valueSupplier);
            }
        });
        return captured;
    }

    @Test
    @DisplayName("Redis 桥：写 host/port，并显式置空哨兵配置")
    void redisBridgeRegistersHostPortAndClearsSentinel() {
        RedisContainer redis = mock(RedisContainer.class);
        Map<String, Supplier<Object>> p =
                capture(new Containers.Redis().redisRawProperties(redis)::accept);

        assertThat(p.keySet()).containsExactlyInAnyOrder(
                "spring.data.redis.host",
                "spring.data.redis.port",
                "spring.data.redis.sentinel.nodes",
                "spring.data.redis.sentinel.master");

        // 哨兵必须是空字符串：RedissonConfig 用 StringUtils.hasText 判分支，
        // 非空就会去连哨兵 —— 而测试里起的是一个单独的 Redis 容器，没有哨兵。
        assertThat(p.get("spring.data.redis.sentinel.nodes").get())
                .as("哨兵地址非空的话 Redisson 会去连不存在的哨兵，报 SENTINEL SENTINELS ...")
                .isEqualTo("");
        assertThat(p.get("spring.data.redis.sentinel.master").get()).isEqualTo("");
    }

    @Test
    @DisplayName("ES 桥：写的是 elasticsearch.host/port —— 应用读的那个顶级自定义属性")
    void elasticsearchBridgeRegistersTheCustomTopLevelProperties() {
        ElasticsearchContainer es = mock(ElasticsearchContainer.class);
        Map<String, Supplier<Object>> p =
                capture(new Containers.Elasticsearch().elasticsearchRawProperties(es)::accept);

        // 不是 spring.elasticsearch.uris —— mall-search 没有 Boot 的 ES 自动配置，
        // 它的 ElasticSearchConfig 读的是顶级的 elasticsearch.host / elasticsearch.port。
        assertThat(p.keySet()).containsExactlyInAnyOrder("elasticsearch.host", "elasticsearch.port");
        assertThat(p.keySet()).noneMatch(k -> k.startsWith("spring.elasticsearch"));
    }
}

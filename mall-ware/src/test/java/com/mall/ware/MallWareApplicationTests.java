package com.mall.ware;

import com.mall.testsupport.MallIntegrationTest;
import com.mall.testsupport.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * 上下文启动的集成测试：用 Testcontainers 起真实中间件，验证这个服务
 * <b>真的能把 Spring 上下文拼起来并连上依赖</b>。
 *
 * <p>本服务需要的容器：Mysql + Rabbit + Redis。
 * 依据是<b>运行中 pod 的 /actuator/health 组件明细</b>，
 * 不是按 classpath 上有什么来猜的 —— 按依赖树猜会得出错误结论，
 * 因为 mall-common 把 mybatis-plus + jdbc starter + MySQL 驱动带给了
 * mall-gateway / mall-auth / mall-cart，而这三个服务并没有 DataSource。
 *
 * <p><b>2026-09-06 补上 Redis。</b>原来只有 Mysql + Rabbit，
 * 依据是当时线上 health 里确实只有 db/rabbit（实测确认过）。
 * 而库存热点读接入多级缓存之后，mall-ware 多了一个对
 * {@code MultiLevelCacheClient} 的<b>硬依赖</b>（WareHotCacheInvalidator
 * 的构造器参数），Redis 从"没有"变成了"起不来就没法启动"。
 * <p>
 * 这一次的顺序值得记一下：是 CI 的这个测试<b>先</b>报出
 * 「No qualifying bean of type MultiLevelCacheClient」，
 * 才发现 mall-ware 的 pom 里压根没有 spring-boot-starter-data-redis
 * （mall-common 里那个是 optional，不传递）。
 * 也就是说它逮到的不是"测试缺个容器"，而是"这个服务线上会 crashloop"。
 *
 * <p>它比看起来值钱：EagerConnectionWarmup 会在启动时真的去开数据库/Redis/MQ 连接，
 * 而那段代码曾经因为 {@code @Bean} 方法签名引用了可能不存在的类型，
 * 一次性把 9 个服务搞进 crashloop。有了这个测试，那类问题在 CI 就会暴露。
 *
 * <p>仍然打着 integration 标签、默认被 surefire 排除：本机没有 Docker，
 * 跑不起来。CI 里有单独一步 {@code mvn -B test -Pintegration} 会跑它们。
 */
@MallIntegrationTest
@Import({Containers.Mysql.class, Containers.Rabbit.class, Containers.Redis.class})
class MallWareApplicationTests {

    @Test
    void contextLoads() {
    }
}

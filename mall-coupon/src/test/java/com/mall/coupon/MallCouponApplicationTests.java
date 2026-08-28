package com.mall.coupon;

import com.mall.testsupport.MallIntegrationTest;
import com.mall.testsupport.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * 上下文启动的集成测试：用 Testcontainers 起真实中间件，验证这个服务
 * <b>真的能把 Spring 上下文拼起来并连上依赖</b>。
 *
 * <p>本服务需要的容器：Mysql + Redis + Rabbit。
 * 依据是<b>运行中 pod 的 /actuator/health 组件明细</b>（health 里有 db/redis/rabbit），
 * 不是按 classpath 上有什么来猜的 —— 按依赖树猜会得出错误结论，
 * 因为 mall-common 把 mybatis-plus + jdbc starter + MySQL 驱动带给了
 * mall-gateway / mall-auth / mall-cart，而这三个服务并没有 DataSource。
 *
 * <p>它比看起来值钱：EagerConnectionWarmup 会在启动时真的去开数据库/Redis/MQ 连接，
 * 而那段代码曾经因为 {@code @Bean} 方法签名引用了可能不存在的类型，
 * 一次性把 9 个服务搞进 crashloop。有了这个测试，那类问题在 CI 就会暴露。
 *
 * <p>仍然打着 integration 标签、默认被 surefire 排除：本机没有 Docker，
 * 跑不起来。CI 里有单独一步 {@code mvn -B test -Pintegration} 会跑它们。
 */
@MallIntegrationTest
@Import({Containers.Mysql.class, Containers.Redis.class, Containers.Rabbit.class})
class MallCouponApplicationTests {

    @Test
    void contextLoads() {
    }
}

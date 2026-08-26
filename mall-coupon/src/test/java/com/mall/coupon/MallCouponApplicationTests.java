package com.mall.coupon;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 脚手架自带的空上下文测试。它要求真实的 MySQL/Redis/RabbitMQ/Consul（以及 mall-search 还要 ES）
 * 全部可达，所以本机跑不了（没有 Docker Desktop）。
 * 打上 integration 标签，默认被 surefire 排除，用 mvn test -Pintegration 才会执行。
 * 待办：改造成 Testcontainers 自带中间件，这样 CI 里能真正跑起来，而不只是被跳过。
 */
@Tag("integration")
@SpringBootTest
class MallCouponApplicationTests {

    @Test
    void contextLoads() {
    }

}

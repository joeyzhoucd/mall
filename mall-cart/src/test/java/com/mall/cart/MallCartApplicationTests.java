package com.mall.cart;

import com.mall.testsupport.Containers;
import com.mall.testsupport.MallIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

/**
 * 上下文启动的集成测试。本服务需要的容器：Redis（health 里只有 redis）。
 *
 * <h3>这个测试是补上一个真实的覆盖缺口</h3>
 * mall-auth / mall-cart / mall-admin 原来<b>一个上下文测试都没有</b>，
 * 只有静态的 ConfigMetadataTest。而 2026-08-27 恰好改了前两个的依赖 ——
 * 把 mall-common 带来的 MyBatis-Plus + JDBC + MySQL 驱动排掉了
 * （它们零使用，原先靠 {@code exclude = DataSourceAutoConfiguration.class} 绕开）。
 * 那种改动一旦排错了 artifact 名，Maven <b>不会报错</b>，只是排除静默失效；
 * 而如果排多了，上下文会在启动时才炸。两种都只有真正起一次上下文才能发现。
 *
 * <p>换句话说：改依赖的同时必须有一个会启动上下文的测试，否则等于改完没验。
 */
@MallIntegrationTest
@Import({Containers.Redis.class})
class MallCartApplicationTests {

    @Test
    void contextLoads() {
    }
}

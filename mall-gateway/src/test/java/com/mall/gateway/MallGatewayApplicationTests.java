package com.mall.gateway;

import com.mall.testsupport.MallIntegrationTest;
import com.mall.testsupport.Containers;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 上下文启动的集成测试：用 Testcontainers 起真实中间件，验证这个服务
 * <b>真的能把 Spring 上下文拼起来并连上依赖</b>。
 *
 * <p>本服务需要的容器：Redis。
 * 依据是<b>运行中 pod 的 /actuator/health 组件明细</b>（health 里只有 redis —— 尽管 mall-common 把 mybatis+MySQL 驱动带了过来，它并没有 DataSource），
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
@Import({Containers.Redis.class})
class MallGatewayApplicationTests {

    /**
     * mall.admin.jwt.public-key 没有默认值（2026-10-09 删了 HS256 和它的公开默认密钥），缺了 AdminAuthFilter 构造即失败。
     * 测试现场生成一对 RSA 密钥、只注入公钥 —— 不往仓库里提交任何密钥材料。
     * （CI 171 就是挂在这里：只给 mall-admin 的集成测试补了私钥，漏了网关这一处。集成测试本机跑不了。）
     */
    @DynamicPropertySource
    static void adminJwtPublicKey(DynamicPropertyRegistry registry) throws Exception {
        java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        String pub = java.util.Base64.getEncoder().encodeToString(g.generateKeyPair().getPublic().getEncoded());
        registry.add("mall.admin.jwt.public-key", () -> pub);
    }

    @Test
    void contextLoads() {
    }
}

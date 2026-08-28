package com.mall.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import com.redis.testcontainers.RedisContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * 各中间件的容器定义。每个服务按自己实际连的东西 {@code @Import} 需要的那几个内部类。
 *
 * <h3>「实际连什么」是实测出来的，不是按 classpath 猜的</h3>
 * 按依赖树判断会得出错误结论：mall-common 把 mybatis-plus + jdbc starter + MySQL 驱动
 * 带给了 mall-gateway / mall-auth / mall-cart，但这三个服务<b>并没有 DataSource</b>。
 * 下面这张表来自运行中 pod 的 {@code /actuator/health} 组件明细
 * （{@code show-details=always} 打开着），是这些服务真正建立了连接的东西：
 * <pre>
 *   服务             db   redis  rabbit
 *   mall-admin       Y     -      -
 *   mall-member      Y     -      -
 *   mall-cart        -     Y      -
 *   mall-coupon      Y     Y      Y
 *   mall-ware        Y     -      Y
 *   mall-order       Y     Y      Y
 *   mall-product     Y     Y      -
 *   mall-auth        -     Y      -
 *   mall-gateway     -     Y      -
 *   mall-search      -     -      -     （用 ES，但没有注册 ES 健康指示器）
 *   mall-thirdparty  -     -      -
 * </pre>
 * 多给一个容器不会让测试失败，只是白等它启动；少给一个则是启动超时后一个
 * 看不出所以然的报错。所以宁可按实测的这张表来，别按「大概需要吧」。
 *
 * <h3>为什么不用 withReuse(true)</h3>
 * 容器复用要求跑测试的机器上有 {@code ~/.testcontainers.properties} 且写了
 * {@code testcontainers.reuse.enable=true}，CI runner 上没有。
 * 设了也不会报错，只是不生效 —— 又一个静默失效。所以不设，
 * 靠 Spring 的上下文缓存在同一个模块内复用即可。
 */
public final class Containers {

    private Containers() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Mysql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(TestImages.MYSQL)
                    // 库名随便取一个：这些测试只验证上下文能起来，不查表。
                    // 真要跑 SQL 的话得先灌 DDL，而 DDL 在仓库根的 db/ 目录下，
                    // 那个目录【不在任何 git 仓库里】，CI 拿不到 —— 见 README 的说明。
                    .withDatabaseName("mall_test")
                    .withUsername("mall")
                    .withPassword("mall");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Redis {
        @Bean
        @ServiceConnection
        RedisContainer redisContainer() {
            // 用 com.redis:testcontainers-redis 提供的 RedisContainer，而不是
            // GenericContainer + @ServiceConnection(name = "redis")。
            // 后者能work，但要靠一个字符串把容器和连接详情工厂对上 —— 写错不报错，
            // 只是没有连接详情被注入，然后应用去连 localhost:6379 并超时。
            // 有专门的类型就用类型，让编译期而不是运行期来管这件事。
            // 这个依赖的版本由 Boot 4.1.1 的 BOM 管理，不用自己钉。
            return new RedisContainer(TestImages.REDIS);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Rabbit {
        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitContainer() {
            return new RabbitMQContainer(TestImages.RABBITMQ);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Elasticsearch {
        @Bean
        @ServiceConnection
        ElasticsearchContainer elasticsearchContainer() {
            return new ElasticsearchContainer(TestImages.ELASTICSEARCH)
                    // 单节点、关安全，否则要配证书和账号，对「上下文能不能起来」毫无价值。
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    // 默认堆对 CI runner 偏大，容易把 2 核 7G 的机器压到 OOM。
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
        }
    }
}

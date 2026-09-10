package com.mall.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
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
 * <h3>为什么要 withReuse(true)</h3>
 * Spring 的上下文缓存只在<b>同一个模块内</b>有效：Maven 给每个模块起一个独立的
 * surefire fork，进程一换，缓存和容器一起没了。于是 11 个模块各起一套中间件，
 * {@code integration-test} 作业实测稳定跑 <b>23 分钟</b>（#99/#100/#101/#102 都是），
 * 而作业超时是 30 分钟 —— 只剩 6 分钟余量，而且和改了多少代码完全无关。
 *
 * <p>{@code withReuse(true)} 让容器在 JVM 退出后【继续活着】，下一个模块的 fork
 * 按容器配置的哈希命中同一个容器，直接连上去。相同配置的容器只启动一次。
 *
 * <h3>它有一个静默失效的前提，必须在 CI 里显式满足</h3>
 * 复用要求跑测试的机器上有 {@code ~/.testcontainers.properties} 且写了
 * {@code testcontainers.reuse.enable=true}。<b>没有这个文件时 withReuse(true)
 * 不报错，只是完全不生效</b> —— 又一个"配了但没生效"。
 * 所以 workflow 里有一步专门写这个文件，还有一步<b>断言日志里出现
 * {@code Reusing container}</b>：前提没满足时 CI 红，而不是悄悄慢回 23 分钟。
 *
 * <h3>为什么跨模块共用一个 MySQL 是安全的</h3>
 * 这个容器起的是<b>空库</b>，不灌任何表（见下面 mysqlContainer 的注释），
 * 这些测试也只验证上下文能不能起来。唯一需要真实表的 mall-admin
 * 用的是自己那个 {@code withInitScript} 的容器 —— 配置不同、哈希不同，
 * 复用不会把它和这个混到一起。
 */
public final class Containers {

    private Containers() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Mysql {
        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            MySQLContainer container = new MySQLContainer(TestImages.MYSQL)
                    // 库名随便取一个：这个容器起的是【空库】，不灌任何表。
                    //
                    // 【这条假设对 mall-admin 不成立，它有自己的容器】
                    // 原来这里写的是「这些测试只验证上下文能起来，不查表」。
                    // 加上定时任务之后 mall-admin 在【启动时】就要查 schedule_job
                    // （ScheduleJobService 的 @PostConstruct 把数据库里的任务装进调度器），
                    // 空库会让上下文起不来 —— CI 实测报
                    //   Table 'mall_test.schedule_job' doesn't exist
                    // 所以 mall-admin 自己定义了带 withInitScript 的容器
                    // （AdminContainers.MysqlWithSchema）。没有把它的表塞进这里，
                    // 是因为那等于让另外 10 个模块也去建它们用不到的表。
                    //
                    // 【如果又有模块需要表】：照 mall-admin 那样在自己模块里定义容器，
                    // 别改这个共享的。真要给这里灌 DDL 的话还有个现实障碍：
                    // 完整 DDL 在仓库根的 db/ 目录下，而那个目录【不在任何 git 仓库里】，
                    // CI 拿不到。
                    .withDatabaseName("mall_test")
                    .withUsername("mall")
                    .withPassword("mall");
            // 复用要用语句而不是链式：withReuse 声明在 GenericContainer 上返回 SELF，
            // 而这里用的是原始类型 MySQLContainer，SELF 会被擦除成 GenericContainer，
            // 链在末尾就接不回方法的返回类型了。下面三个容器同理。
            container.withReuse(true);
            return container;
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
            RedisContainer container = new RedisContainer(TestImages.REDIS);
            container.withReuse(true);
            return container;
        }

        /**
         * 把容器地址额外写进 spring.data.redis.host / port。
         *
         * <p>光有 @ServiceConnection 不够 —— 它提供的是 ConnectionDetails bean，
         * 由自动配置消费，【不会写进 Environment】。而 mall-coupon 和 mall-product 的
         * RedissonConfig 是用 @Value("${spring.data.redis.host}") 直接读属性自己建
         * RedissonClient 的，拿不到容器地址就会去连 application.yml 里的默认值
         * localhost:6379，而 Redisson 启动时就建连接 —— 上下文直接起不来。
         *
         * <p>症状有迷惑性：@ServiceConnection 明明"配了"、容器也确实起来了，
         * 但应用连的是另一个地址。
         *
         * <p><b>哨兵：这条注释预言的情况已经发生了，所以下面显式置空。</b>
         * 原来的判断是「测试上下文里没人设 sentinel.nodes，RedissonConfig 会自动
         * 落到单机模式」。但各服务的 application.yml 写的是
         * {@code nodes: ${REDIS_SENTINEL_NODES:redis-sentinel-0.redis-sentinel:26379,...}}
         * —— 带默认值的占位符，也就是<b>即使环境变量没设，属性也永远非空</b>，
         * 于是 {@code StringUtils.hasText(sentinelNodes)} 恒为真，测试里的 Redisson
         * 会去连集群里那三个哨兵。CI 实测报的是：
         * {@code SENTINEL SENTINELS command returns empty result or connection
         * can't be established to some of them}。
         *
         * <p>这里起的是一个<b>单独的 Redis 容器</b>，没有哨兵也不该有，
         * 所以显式把 sentinel.nodes / master 置空，让分支落回单机。
         * 动态属性的优先级高于 application.yml，置空是有效的覆盖。
         */
        @Bean
        DynamicPropertyRegistrar redisRawProperties(RedisContainer redis) {
            return (registry) -> {
                registry.add("spring.data.redis.host", redis::getHost);
                registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
                // 见上面注释：yml 里的 sentinel.nodes 带默认值，永远非空，必须显式置空。
                registry.add("spring.data.redis.sentinel.nodes", () -> "");
                registry.add("spring.data.redis.sentinel.master", () -> "");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Rabbit {
        @Bean
        @ServiceConnection
        RabbitMQContainer rabbitContainer() {
            RabbitMQContainer container = new RabbitMQContainer(TestImages.RABBITMQ);
            container.withReuse(true);
            return container;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class Elasticsearch {
        /**
         * 这里【故意】不加 @ServiceConnection。
         *
         * <p>加了会直接失败：
         * {@code ConnectionDetailsNotFoundException: No ConnectionDetails found for source
         * '@ServiceConnection source for Bean 'elasticsearchContainer''}。
         * 原因是 {@code @ServiceConnection} 需要 classpath 上有能消费它的
         * ConnectionDetails 工厂，而那个工厂来自 Boot 的 Elasticsearch 自动配置 ——
         * <b>mall-search 压根没有引 Boot 的 ES starter</b>，它直接用裸的
         * elasticsearch-java 自己建 RestClient（为了自己控制 Jackson 3 的 transport）。
         *
         * <p>也就是说对这个服务来说 {@code @ServiceConnection} 无从发挥：
         * 既没有自动配置会读 ConnectionDetails，也没有工厂能产出它。
         * 容器地址只能通过下面的 DynamicPropertyRegistrar 写进
         * {@code elasticsearch.host} / {@code elasticsearch.port} —— 那才是
         * ElasticSearchConfig 真正读的属性。
         *
         * <p>容器本身仍然会被启动：Boot 的 Testcontainers 支持会启动所有
         * {@code Startable} 类型的 bean，和有没有 {@code @ServiceConnection} 无关。
         */
        @Bean
        ElasticsearchContainer elasticsearchContainer() {
            ElasticsearchContainer container = new ElasticsearchContainer(TestImages.ELASTICSEARCH)
                    // 单节点、关安全，否则要配证书和账号，对「上下文能不能起来」毫无价值。
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    // 默认堆对 CI runner 偏大，容易把 2 核 7G 的机器压到 OOM。
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
            container.withReuse(true);
            return container;
        }

        /**
         * 把容器地址写进 elasticsearch.host / elasticsearch.port。
         *
         * <p>mall-search 的 ElasticSearchConfig 用 @Value("${elasticsearch.host}")
         * 自己建 RestClient —— 注意那是个【顶级自定义属性】，连 Boot 的
         * spring.elasticsearch.* 都不是，所以 @ServiceConnection 完全够不着它，
         * 测试里会去连 application.yml 的默认值 192.168.77.102:9200。
         *
         * <p>更根本的做法是让 ElasticSearchConfig 改用 Boot 自动配置的
         * ElasticsearchClient，那样 @ServiceConnection 就够了。但那个配置类
         * 专门处理了 Jackson 3 的 transport，改动风险更大，先用这层桥接把测试跑起来。
         */
        @Bean
        DynamicPropertyRegistrar elasticsearchRawProperties(ElasticsearchContainer es) {
            return (registry) -> {
                registry.add("elasticsearch.host", es::getHost);
                registry.add("elasticsearch.port", () -> es.getMappedPort(9200));
            };
        }
    }
}

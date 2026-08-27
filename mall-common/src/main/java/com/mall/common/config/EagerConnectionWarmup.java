package com.mall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 启动时主动把外部连接建好，不要留给第一个请求。
 *
 * <h3>为什么需要这个（2026-08-27 压测实测）</h3>
 * mall-coupon 充分预热后在 500m CPU 下轻松吃下 70 rps（2100/2100 成功，p95 82ms）。
 * 但<b>刚启动的同一个服务，50 rps 就会把自己搞死</b>：p95 约 10 秒、大量客户端超时、
 * CPU 100%，最后连存活探针都响应不过来，被 K8s SIGKILL，重启后又是冷的 ——
 * 死亡螺旋能自我维持。复现过两次。
 * <p>
 * 日志把原因说得很清楚：这些本该在启动阶段做完的事，全都发生在<b>第一个业务请求的
 * 线程上</b>（注意 thread=tomcat-handler-1）：
 * <pre>
 * thread=tomcat-handler-1 msg=Attempting to connect to: rabbitmq:5672
 * thread=tomcat-handler-1 msg=HikariPool-1 - Starting...
 * thread=tomcat-handler-1 msg=HikariPool-1 - Added connection ...
 * </pre>
 * 空闲时这点开销无所谓；秒杀这种「零点瞬间来一大波」的场景里，它正好落在
 * 最不能承受的时刻。实测搬走约 2.1 秒（Redis 101ms + RabbitMQ 1203ms + 连接池 804ms）。
 *
 * <h3>为什么放在 ApplicationRunner 里</h3>
 * Spring Boot 的 readiness 探针在 {@code ApplicationReadyEvent} 之后才变成 UP，而
 * {@link ApplicationRunner} 在那之前执行。所以这里的耗时发生在<b>K8s 把流量切进来
 * 之前</b>，代价由启动时间承担，而不是由第一批用户承担。
 * <p>
 * 放在 {@code ApplicationReadyEvent} 监听器里就不行 —— 那时探针可能已经放行，
 * 预热会和真实流量抢 CPU，反而更糟。
 *
 * <h3>失败只警告、不中断启动</h3>
 * 和本仓库既有的选择一致（各服务的 Config Server 地址都写成
 * {@code optional:configserver:}，就是为了不让依赖的启动顺序变成 crashloop）。
 * 预热失败说明某个中间件此刻不可用，那本来就该由探针和重试处理，
 * 不该因为「预热没成功」让一个功能完好的服务起不来。
 *
 * <h3>为什么每种连接各自放在【嵌套静态类】里 —— 这一段是踩过坑才写对的</h3>
 * 这三样东西不是每个服务都有：Redis 和 RabbitMQ 的依赖在 mall-common 里声明成
 * {@code optional}（编译期可见但不传递），所以 mall-auth / mall-gateway 这些不用 MQ
 * 的服务 classpath 上<b>没有</b> {@code org.springframework.amqp.rabbit.connection.ConnectionFactory}。
 * <p>
 * <b>第一版把三个 {@code @Bean} 都放在这个外层类里，只在方法上挂 {@code @ConditionalOnClass}，
 * 结果 9 个服务全部启动失败</b>：
 * <pre>
 * Failed to introspect Class [com.mall.common.config.EagerConnectionWarmup]
 * Caused by: NoClassDefFoundError: org/springframework/amqp/rabbit/connection/ConnectionFactory
 *   at java.lang.Class.getDeclaredMethods0(Native Method)
 *   at org.springframework.util.ReflectionUtils.getDeclaredMethods(...)
 * </pre>
 * 当时我以为「Spring 用 ASM 读注解判定条件，不会为了判定去加载方法参数里的类型」。
 * <b>那个理解是错的</b>：Spring 要先调 {@code Class.getDeclaredMethods()} 才能找到
 * {@code @Bean} 方法，而这一步<b>会解析每个方法签名上的全部类型</b> ——
 * 方法级的条件注解还没轮到被求值，类的自省就已经抛 NoClassDefFoundError 了。
 * <p>
 * 正确做法就是现在这样：把引用了「可能不存在的类」的 {@code @Bean} 放进各自的
 * <b>嵌套静态类</b>，把 {@code @ConditionalOnClass} 挂在<b>类</b>上。条件不成立时
 * 这个嵌套类整体不会被自省，签名里的类型也就永远不会被解析。
 * Spring Boot 自己到处是这个写法（例如 {@code DataSourceConfiguration$Hikari}）——
 * 它不是风格偏好，是唯一能工作的方式。
 * <p>
 * 附带一条更贵的教训：这个 bug 上线后我<b>只验证了 mall-coupon</b>（它通过
 * mall-mq-starter 有 amqp，所以恰好是唯一不受影响的服务），就以为整批都好了。
 * 另外 9 个服务一直在 CrashLoopBackOff，而 K8s 的滚动更新保留了旧 pod 提供服务，
 * 所以外部看起来一切正常。<b>验证要覆盖「条件不同的那一类」，不是随便挑一个。</b>
 * {@link EagerConnectionWarmupTest} 里用 {@code FilteredClassLoader} 模拟
 * 「classpath 上没有这些类」，就是为了让这个错误在本地测试里直接失败。
 *
 * <h3>这个类没有解决的部分</h3>
 * JIT 编译是另一半冷启动成本，只能靠真正执行代码路径来消除，这里做不到。
 * <b>不要因为加了这个类就认为冷启动问题已经解决</b> —— 它只搬走了连接初始化那一部分
 * （实测冷态 50 rps 的 p95 从 3213ms 降到 2785ms，而热态是 82ms，差距仍有 34 倍）。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mall.warmup.enabled", matchIfMissing = true)
public class EagerConnectionWarmup {

    private static final Logger log = LoggerFactory.getLogger(EagerConnectionWarmup.class);

    /**
     * {@code javax.sql.DataSource} 来自 JDK，任何服务都有，所以这个可以留在外层。
     * 用 ObjectProvider 是因为不连关系型数据库的服务（mall-search / mall-thirdparty）
     * 没有 DataSource bean —— 那种情况下 forEach 不会执行，不需要条件注解。
     */
    @Bean
    ApplicationRunner mallDataSourceWarmup(ObjectProvider<DataSource> dataSources) {
        return args -> dataSources.forEach(ds -> warmup("数据库连接池", () -> {
            // getConnection 就足够触发 Hikari 建池；try-with-resources 立刻归还，
            // 连接本身留在池里 —— 那才是要的效果。
            try (java.sql.Connection c = ds.getConnection()) {
                c.isValid(2);
            }
        }));
    }

    /** 见类注释：引用了可能不存在的类，必须放在带类级 @ConditionalOnClass 的嵌套类里。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.data.redis.connection.RedisConnectionFactory.class)
    static class RedisWarmupConfiguration {

        @Bean
        ApplicationRunner mallRedisWarmup(
                ObjectProvider<org.springframework.data.redis.connection.RedisConnectionFactory> factories) {
            return args -> factories.forEach(f -> warmup("Redis 连接", () -> {
                try (org.springframework.data.redis.connection.RedisConnection c = f.getConnection()) {
                    c.ping();
                }
            }));
        }
    }

    /** 见类注释：这个就是把 9 个服务搞崩的那一个，现在被类级条件挡住了。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
    static class RabbitWarmupConfiguration {

        @Bean
        ApplicationRunner mallRabbitWarmup(
                ObjectProvider<org.springframework.amqp.rabbit.connection.ConnectionFactory> factories) {
            return args -> factories.forEach(f -> warmup("RabbitMQ 连接", () -> {
                // 刻意不 close()：CachingConnectionFactory 的 close 会把缓存的连接一起关掉，
                // 那样预热就白做了。这里只要把连接建立起来，之后由工厂自己管理。
                org.springframework.amqp.rabbit.connection.Connection c = f.createConnection();
                if (!c.isOpen()) {
                    throw new IllegalStateException("连接已建立但状态是 closed");
                }
            }));
        }
    }

    /** 包级可见（不是 private）：嵌套的两个配置类要用它。 */
    static void warmup(String what, ThrowingRunnable action) {
        long start = System.currentTimeMillis();
        try {
            action.run();
            log.info("启动预热: {} 已就绪, 耗时 {} ms（这段成本原本会落在第一批请求上）",
                    what, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // 只警告，不抛。见类注释「失败只警告、不中断启动」。
            log.warn("启动预热: {} 失败，改由第一个请求惰性初始化: {}", what, e.toString());
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}

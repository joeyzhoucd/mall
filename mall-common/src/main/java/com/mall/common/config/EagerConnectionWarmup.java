package com.mall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

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
 * 也就是说第一批请求除了自己的业务，还要顺带承担建 TCP 连接、握手、认证、建连接池
 * 的成本。空闲时这点开销无所谓；秒杀这种「零点瞬间来一大波」的场景里，
 * 它正好落在最不能承受的时刻。
 *
 * <h3>为什么放在 ApplicationRunner 里</h3>
 * Spring Boot 的 readiness 探针在 {@code ApplicationReadyEvent} 之后才变成 UP，而
 * {@link ApplicationRunner} 在那之前执行。所以这里的耗时发生在<b>K8s 把流量切进来
 * 之前</b>，代价由启动时间承担（约 1 秒），而不是由第一批用户承担。
 * <p>
 * 放在 {@code ApplicationReadyEvent} 监听器里就不行 —— 那时探针可能已经放行，
 * 预热会和真实流量抢 CPU，反而更糟。
 *
 * <h3>失败只警告、不中断启动</h3>
 * 和本仓库既有的选择一致（各服务的 Config Server 地址都写成
 * {@code optional:configserver:}，就是为了不让依赖的启动顺序变成 crashloop）。
 * 预热失败说明某个中间件此刻不可用，那本来就该由探针和重试去处理，
 * 不该因为「预热没成功」让一个功能完好的服务起不来。
 *
 * <h3>三个 Bean 方法各自带 @ConditionalOnClass 的原因</h3>
 * 这三样东西不是每个服务都有：mall-search / mall-thirdparty 不连关系型数据库，
 * 只有部分服务用 MQ 和 Redis。所以
 * <ul>
 *   <li>依赖在 mall-common 里声明成 {@code optional}：编译期可见，但不会传递给
 *       所有服务，没用到的服务 classpath 上就没有这些类；</li>
 *   <li>每个方法用 {@code @ConditionalOnClass} 守住。Spring 用 ASM 读注解来判定条件，
 *       不会为了判定而加载方法参数里的类型，所以类不存在时方法整体不会被处理 ——
 *       这是 Spring Boot 自己到处在用的写法。写成一个方法接三个参数就做不到这一点，
 *       缺任何一个都会 NoClassDefFoundError。</li>
 * </ul>
 *
 * <h3>这个类没有解决的部分</h3>
 * JIT 编译是另一半冷启动成本，只能靠真正执行代码路径来消除，这里做不到。
 * 想进一步压缩的方向是 CDS（类数据共享）、Spring AOT/原生镜像，
 * 以及灰度时先用小流量把实例喂热再放大。
 * <b>不要因为加了这个类就认为冷启动问题已经解决</b> —— 它只搬走了连接初始化那一部分。
 */
@AutoConfiguration
@ConditionalOnProperty(name = "mall.warmup.enabled", matchIfMissing = true)
public class EagerConnectionWarmup {

    private static final Logger log = LoggerFactory.getLogger(EagerConnectionWarmup.class);

    @Bean
    @ConditionalOnClass(DataSource.class)
    ApplicationRunner mallDataSourceWarmup(ObjectProvider<DataSource> dataSources) {
        return args -> dataSources.forEach(ds -> warmup("数据库连接池", () -> {
            // getConnection 就足够触发 Hikari 建池；try-with-resources 立刻归还，
            // 连接本身留在池里 —— 那才是要的效果。
            try (java.sql.Connection c = ds.getConnection()) {
                c.isValid(2);
            }
        }));
    }

    @Bean
    @ConditionalOnClass(org.springframework.data.redis.connection.RedisConnectionFactory.class)
    ApplicationRunner mallRedisWarmup(
            ObjectProvider<org.springframework.data.redis.connection.RedisConnectionFactory> factories) {
        return args -> factories.forEach(f -> warmup("Redis 连接", () -> {
            try (org.springframework.data.redis.connection.RedisConnection c = f.getConnection()) {
                c.ping();
            }
        }));
    }

    @Bean
    @ConditionalOnClass(org.springframework.amqp.rabbit.connection.ConnectionFactory.class)
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

    private void warmup(String what, ThrowingRunnable action) {
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
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}

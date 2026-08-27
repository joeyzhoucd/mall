package com.mall.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * 启动时用合成请求把热路径跑一遍，强制 JIT 编译，在 readiness 变 UP 之前完成。
 *
 * <h3>为什么需要它，以及它和 {@link EagerConnectionWarmup} / CDS 的分工</h3>
 * 冷启动的代价可以拆成三块，三块要用三种手段，<b>互相替代不了</b>：
 * <table border="1">
 *   <tr><th>成本</th><th>手段</th><th>实测</th></tr>
 *   <tr><td>建连接（连接池 / MQ / Redis 握手）</td>
 *       <td>{@link EagerConnectionWarmup}</td>
 *       <td>搬走约 2.1–3.5 秒；冷态 50 rps 的 p95 从 3213ms 降到 2785ms</td></tr>
 *   <tr><td>解析 class 文件、构建元数据</td>
 *       <td>CDS（见各服务 Dockerfile）</td>
 *       <td>归档命中率 95%，启动收益待在集群量</td></tr>
 *   <tr><td><b>请求路径上的 JIT 未编译</b></td>
 *       <td><b>这个类</b></td>
 *       <td>冷态 p95 2785ms 对热态 82ms，<b>差 34 倍</b> —— 剩下的几乎全在这里</td></tr>
 * </table>
 * 前两个都不碰 JIT。CDS 只优化类加载；建好连接也不会让字节码被编译成机器码。
 * 而实测最大的那一块恰恰是 JIT：同样 50 rps，热的 p95 76–82ms 从容处理，
 * 冷的 p95 约 10 秒、CPU 100%、被存活探针 SIGKILL（复现过两次）。
 *
 * <h3>为什么是「自己给自己发 HTTP 请求」而不是直接调 Service 方法</h3>
 * 要编译的不只是业务代码，更多是它下面那一整叠框架：Tomcat 的连接处理、
 * 过滤器链、DispatcherServlet 的 HandlerMapping/HandlerAdapter、参数解析、
 * Jackson 的序列化。直接调 Service 方法把这一叠全跳过了，而它们才是
 * 「每个请求都要走一遍」的部分。
 * <p>
 * 请求打 127.0.0.1 上自己的端口，不经过网关、不进入服务发现，
 * 所以不会有流量被误导过来。
 *
 * <h3>为什么不预热真正的抢购接口</h3>
 * 那条接口有副作用：扣 Redis 库存、写本地消息表、可能发 MQ。用它预热等于
 * 每次 pod 启动都凭空卖掉一批货。所以默认只打 {@code /actuator/health/liveness} ——
 * 它便宜、幂等，而且<b>照样要走完整的 servlet + 过滤器 + DispatcherServlet 链路</b>，
 * 那正是要编译的部分。
 * <p>
 * 需要更贴近业务的预热时，用 {@code mall.warmup.request.paths} 配上该服务自己的
 * <b>只读</b>端点（例如某个 list 接口）。<b>配之前务必确认它没有副作用</b> ——
 * 这个配置项能把任意路径打 200 次。
 *
 * <h3>为什么放在 ApplicationRunner 里、以及为什么必须失败只警告</h3>
 * ApplicationRunner 跑在 Tomcat 已启动、而 {@code ApplicationReadyEvent} 尚未发布之间，
 * 所以 readiness 探针还是 DOWN，K8s 不会把流量切进来 —— 预热的代价由启动时间承担。
 * <p>
 * 任何一步失败都只打警告：预热是优化，不是功能。让一个「优化没做成」演变成
 * 「服务起不来」是纯粹的倒退。这一点在本仓库刚刚付过学费：
 * {@link EagerConnectionWarmup} 第一版因为把 @Bean 放错位置，让 9 个服务全部
 * CrashLoopBackOff。
 *
 * <h3>默认 200 次的依据（以及为什么不该指望它把 JIT 喂满）</h3>
 * HotSpot 分层编译下，C1 大约在数百次调用后介入，C2 要上万次。200 次自请求
 * 只够把框架层推进 C1，<b>拿不到 C2 的峰值性能</b>。所以这个类<b>不能宣称解决了
 * 冷启动问题</b>，它只是把曲线的前半段抬起来。真要接近热态还得靠灰度：
 * 新实例先接一小部分流量，喂热了再放大。
 * <p>
 * 次数是可调的（{@code mall.warmup.request.iterations}）。调大它换来的是更长的
 * 启动时间 —— 而启动时间也不是免费的，K8s 滚动更新期间集群要同时跑两份实例。
 */
@AutoConfiguration(after = EagerConnectionWarmup.class)
@ConditionalOnProperty(name = "mall.warmup.request.enabled", matchIfMissing = true)
public class RequestPathWarmup {

    private static final Logger log = LoggerFactory.getLogger(RequestPathWarmup.class);

    /**
     * 注意方法签名里只出现 JDK 类型和 Spring 核心类型。
     * <b>不要在这里引用任何 optional 依赖的类型</b> —— Spring 为了找 @Bean 方法会调
     * {@code Class.getDeclaredMethods()}，那一步会解析全部方法签名，方法级的
     * {@code @ConditionalOnClass} 还没轮到求值。详见 {@link EagerConnectionWarmup} 的类注释。
     */
    @Bean
    ApplicationRunner mallRequestPathWarmup(ApplicationContext context,
                                            ObjectProvider<DataSource> dataSources,
                                            org.springframework.core.env.Environment env) {
        return args -> {
            int iterations = env.getProperty("mall.warmup.request.iterations", Integer.class, 200);
            List<String> paths = List.of(env.getProperty("mall.warmup.request.paths",
                    "/actuator/health/liveness").split(","));

            warmDatabase(dataSources, iterations);
            warmHttp(context, paths, iterations);
        };
    }

    /**
     * 平凡查询，只为编译 MyBatis/JDBC/HikariCP 那一叠 —— 以及本仓库额外加的
     * observation 代理（见 {@link JdbcObservationAutoConfiguration}，它给每次
     * 取连接和每条 SQL 都多包了一层）。
     * <p>
     * 用 {@code SELECT 1} 而不是真实业务查询：无副作用、不依赖任何表存在，
     * 换任何环境都能跑。代价是编译不到具体 Mapper 的代码路径，
     * 但那部分占比远小于框架层。
     */
    private void warmDatabase(ObjectProvider<DataSource> dataSources, int iterations) {
        dataSources.ifAvailable(ds -> {
            long start = System.currentTimeMillis();
            int ok = 0;
            for (int i = 0; i < iterations; i++) {
                try (java.sql.Connection c = ds.getConnection();
                     java.sql.Statement st = c.createStatement()) {
                    st.execute("SELECT 1");
                    ok++;
                } catch (Exception e) {
                    if (i == 0) {
                        log.warn("请求路径预热: 数据库预热失败，跳过: {}", e.toString());
                    }
                    break;
                }
            }
            log.info("请求路径预热: 数据库 {} 次, 耗时 {} ms", ok, System.currentTimeMillis() - start);
        });
    }

    /**
     * 自请求。端口从 {@link WebServerApplicationContext} 取实际监听端口，
     * 而不是读 {@code server.port} 配置 —— 配成 0（随机端口）时后者拿不到真实值。
     */
    private void warmHttp(ApplicationContext context, List<String> paths, int iterations) {
        int port = resolvePort(context);
        if (port <= 0) {
            log.info("请求路径预热: 非 servlet/web 应用或端口未知，跳过 HTTP 预热");
            return;
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        for (String rawPath : paths) {
            String path = rawPath.trim();
            if (path.isEmpty()) {
                continue;
            }
            long start = System.currentTimeMillis();
            int ok = 0;
            for (int i = 0; i < iterations; i++) {
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + path))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    // discarding：只要请求走完整条链路，响应体不需要留下来
                    client.send(req, HttpResponse.BodyHandlers.discarding());
                    ok++;
                } catch (Exception e) {
                    // 第一次就失败说明路径不对或服务没就绪，不值得再试 199 次
                    if (i == 0) {
                        log.warn("请求路径预热: {} 首次请求失败，跳过该路径: {}", path, e.toString());
                        break;
                    }
                    // 中途偶发失败无所谓，预热本来就不保证每次都成功
                }
            }
            log.info("请求路径预热: {} 打了 {} 次, 耗时 {} ms（这段 JIT 编译原本会落在第一批请求上）",
                    path, ok, System.currentTimeMillis() - start);
        }
    }

    private int resolvePort(ApplicationContext context) {
        if (context instanceof WebServerApplicationContext webContext) {
            var server = webContext.getWebServer();
            return server != null ? server.getPort() : -1;
        }
        return -1;
    }

}

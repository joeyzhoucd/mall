package com.mall.order.reco.batch;

import com.mall.order.reco.batch.BatchStore.BatchStatus;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * 「搭配购买」批任务入口，由 K8s CronJob 用 mall-order 镜像启动：
 * <pre>java -cp /app/extracted/app.jar com.mall.order.reco.batch.ComplementBatchMain</pre>
 *
 * <h3>为什么是裸 main，而不是「mall-order 换个 profile 启动」</h3>
 * mall-order 的 Spring 上下文一起来，就会带起它的全部后台行为：@EnableScheduling 下的
 * outbox 发布 / 支付对账定时任务、RabbitMQ 消费者、Consul 注册。批任务 pod 里跑这些，
 * 等于多了一个会抢消息、会被网关当成可用实例的 mall-order —— 而这些行为没有一个开关能统一关掉。
 * 批任务只需要一条 JDBC 连接，所以就只拿一条 JDBC 连接。
 *
 * <h3>退出码</h3>
 * 0 = ACTIVE / READY；2 = 质量闸门拒绝（REJECTED）；1 = 失败（FAILED 或连不上库）。
 * 非 0 时 K8s 把这次 Job 记为失败 —— 告警挂在那上面，而不是在日志里找。
 */
public final class ComplementBatchMain {

    private ComplementBatchMain() {
    }

    public static void main(String[] args) {
        System.exit(run(BatchConfig.fromEnv(System.getenv())));
    }

    static int run(BatchConfig config) {
        try (Connection conn = DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password())) {
            ComplementBatch.Result r = new ComplementBatch(new JdbcBatchStore(conn), System::currentTimeMillis).run(config);
            System.out.printf("RESULT batch=%d status=%s orders=%d spus=%d rows=%d prev=%s ms=%d reason=%s%n",
                    r.batchId(), r.status(), r.stats().ordersRead(), r.stats().spusCovered(), r.stats().rowsWritten(),
                    r.stats().prevSpusCovered(), r.stats().durationMs(), r.reason());
            return r.status() == BatchStatus.ACTIVE || r.status() == BatchStatus.READY ? 0
                    : r.status() == BatchStatus.REJECTED ? 2 : 1;
        } catch (Exception e) {
            System.err.println("批任务无法运行：" + e);
            e.printStackTrace();
            return 1;
        }
    }
}

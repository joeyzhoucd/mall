package com.mall.order.submit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 下单闸门 + 三个指标。
 *
 * <h3>默认 8（2026-09-29 校准）</h3>
 * 最初是 4：那时 submitOrder 整个是一个事务、每单占连接 ~400ms，闸门实际守的是池（5 条，留 1 给
 * outbox / 支付回调 / 关单）。34e5ca3 把事务缩到只剩落库、1712114 把 outbox 发送挪出提交线程之后，
 * 在途下单大部分时间不再占连接，闸门守的变成下游。同一探针（每单 4 件、JVM 已热）：
 * <pre>
 *   上限  最高吞吐   放行成功率  池超时  池排队峰值  ware 锁库存 p95/p99
 *     4   12 单/s    100%        0       0           212 / 371ms
 *     8   19 单/s    100%        0       0           383 / 702ms
 *    16   25 单/s    100%        0       1           549 / 749ms
 * </pre>
 * 取 8 而不是 16：下一个瓶颈是 MySQL 写入（每次提交 ~100ms、buffer pool 还是默认 128MB），
 * 它是全部服务共用的，16 会让下单把更多写压到它上面、拖慢别人。MySQL 调好之后再评估。
 * 判据不变：{@code hikaricp_connections_timeout_total} 为 0、放进来的 100% 成功、被拒的快速返回；
 * 调参用内部接口运行时改，不用重启。
 */
@Configuration
public class SubmitGateConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SubmitGateConfiguration.class);

    @Bean
    public SubmitGate submitGate(@Value("${mall.order.submit.gate.limit:8}") int limit) {
        log.info("下单闸门: 上限={}（每个 pod；校准数据见 SubmitGateConfiguration）", limit);
        return new SubmitGate(limit);
    }

    @Bean
    public Gauge orderSubmitGateLimit(MeterRegistry registry, SubmitGate gate) {
        return Gauge.builder("order.submit.gate.limit", gate, SubmitGate::limit)
                .description("下单闸门当前上限（每个 pod，可运行时改）")
                .register(registry);
    }

    @Bean
    public Gauge orderSubmitGateInFlight(MeterRegistry registry, SubmitGate gate) {
        return Gauge.builder("order.submit.gate.in.flight", gate, SubmitGate::inFlight)
                .description("正在提交中的订单数。长期贴着上限说明闸门是瓶颈")
                .register(registry);
    }

    /** Micrometer 会剥掉 Gauge 名上的 _total，Prometheus 里是 order_submit_gate_rejected（和秒杀闸门同样的约定） */
    @Bean
    public Gauge orderSubmitGateRejected(MeterRegistry registry, SubmitGate gate) {
        return Gauge.builder("order.submit.gate.rejected.total", gate, SubmitGate::rejectedCount)
                .description("被下单闸门挡下的请求累计数（进程内计数，pod 重启归零）")
                .register(registry);
    }
}

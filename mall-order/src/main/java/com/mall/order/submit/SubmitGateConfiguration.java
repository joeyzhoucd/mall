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
 * <h3>默认 4：池是 5，留 1 条给别人</h3>
 * 同一个池还要服务 outbox 发布（每 5s）、支付回调、关单消费者、结算页。闸门等于池大小的话，
 * 高峰时下单能把 5 条全占住，这些后台路径反而拿不到连接 —— 下单保住了，支付回调超时了。
 * 起点按这个推，<b>校准结果以压测为准</b>（判据：{@code hikaricp_connections_timeout_total} 为 0，
 * 放进来的 100% 成功，被拒的秒回），校准时用内部接口运行时改，不用重启。
 */
@Configuration
public class SubmitGateConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SubmitGateConfiguration.class);

    @Bean
    public SubmitGate submitGate(@Value("${mall.order.submit.gate.limit:4}") int limit) {
        log.info("下单闸门: 上限={}（每个 pod；池 5 留 1 给后台路径，校准见 SubmitGateConfiguration）", limit);
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

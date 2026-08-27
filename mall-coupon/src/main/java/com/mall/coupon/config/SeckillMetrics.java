package com.mall.coupon.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 把秒杀闸门的状态暴露成 Prometheus 指标。
 *
 * <h3>为什么单独一个类，而不是把 MeterRegistry 注进 SeckillBulkhead</h3>
 * {@link SeckillBulkhead} 是个纯粹的并发原语，构造它只需要一个容量数字。
 * 一旦它的构造器多出一个 MeterRegistry 参数，单元测试就没法再用
 * {@code new SeckillBulkhead(2)} 直接构造，得先造一个假的注册表 ——
 * 为了埋点而让核心逻辑变得难测试，是笔亏本买卖。
 * 观测是"围绕"核心逻辑的关注点，放在外面接线更合适。
 *
 * <h3>为什么这几个指标值得埋</h3>
 * 限流如果没有指标就是个黑盒：拒绝率高到底是"阈值定小了"还是"下游真的扛不住"，
 * 光看应用日志分不出来。有了这两个指标就能直接回答：
 * <ul>
 *   <li>{@code seckill_bulkhead_available_permits} 长期贴近 0 → 闸门是当前的瓶颈，
 *       此时再看下游（数据库连接池使用率、Redis 延迟）是否还有余量，有余量才该调大容量；</li>
 *   <li>{@code seckill_bulkhead_rejected_total} 的增长速率 → 到底有多少用户被挡在门外，
 *       这是评估"限流是否伤害了正常用户"的唯一依据。</li>
 * </ul>
 * 拒绝数用 Gauge 而不是 Counter：计数本身由 SeckillBulkhead 内部的 LongAdder 维护，
 * 这里只是把它读出来。用 Counter 就得让业务代码去调 increment，等于把埋点又塞回核心逻辑里。
 * Prometheus 端对单调递增的 Gauge 一样可以用 rate()，只是要注意 pod 重启后会归零 ——
 * 这一点和进程内计数器的语义一致，不是问题。
 */
@Configuration
public class SeckillMetrics {

    @Bean
    public Gauge seckillBulkheadAvailablePermits(MeterRegistry registry, SeckillBulkhead bulkhead) {
        return Gauge.builder("seckill.bulkhead.available.permits", bulkhead, SeckillBulkhead::availablePermits)
                .description("秒杀闸门当前剩余的通行证数量。长期贴近 0 说明闸门已成为瓶颈")
                .register(registry);
    }

    @Bean
    public Gauge seckillBulkheadCapacity(MeterRegistry registry, SeckillBulkhead bulkhead) {
        return Gauge.builder("seckill.bulkhead.capacity", bulkhead, SeckillBulkhead::capacity)
                .description("秒杀闸门的容量上限。和 available.permits 一起才能算出使用率")
                .register(registry);
    }

    @Bean
    public Gauge seckillBulkheadRejected(MeterRegistry registry, SeckillBulkhead bulkhead) {
        return Gauge.builder("seckill.bulkhead.rejected.total", bulkhead, SeckillBulkhead::rejectedCount)
                .description("累计被闸门挡下的请求数（进程内计数，pod 重启归零）")
                .register(registry);
    }
}

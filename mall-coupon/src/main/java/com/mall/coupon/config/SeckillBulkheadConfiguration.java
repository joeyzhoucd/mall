package com.mall.coupon.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 选一个闸门实现。
 *
 * <h3>为什么默认切到自适应</h3>
 * 压测证明真实容量在冷热之间差一个数量级，任何固定值都是折中（详见
 * {@link AdaptiveSeckillBulkhead} 的类注释）。所以默认用自适应 —— 形状对症。
 *
 * <h3>为什么静态实现必须保留</h3>
 * 两个理由，第二个更重要：
 * <ol>
 *   <li>逃生舱：自适应引入了一个会自己变的量，出问题时能一键切回可预测的行为。</li>
 *   <li><b>它是实验器材。</b>「自适应比静态好」目前只是基于原理的判断，
 *       <b>不是本环境的实测结论</b> —— 这套环境的测量噪声很大（同一配置两轮 p95
 *       能差一个数量级，见 JdbcObservationAutoConfiguration 的记录）。
 *       要证明它，只能跑多轮 A/B，而 A/B 需要对照组。</li>
 * </ol>
 * 切换方式（{@code kubectl set env} 或 chart 的 values）：
 * <pre>
 *   mall.seckill.bulkhead.mode=adaptive   # 默认
 *   mall.seckill.bulkhead.mode=static
 * </pre>
 * 注意 ArgoCD 开着自动同步时会把 {@code kubectl set env} 还原掉 ——
 * 做对照实验前先暂停 {@code syncPolicy.automated}，做完记得恢复。
 * 这一条已经吃过亏：前几轮 CPU 对照实验的数据全部作废，就是因为改动被悄悄还原了。
 *
 * <h3>为什么写成 if/else 而不是两个 @ConditionalOnProperty 的 Bean</h3>
 * 条件注解的写法在「配了一个无效值」时会静默地两个 bean 都不生成，
 * 然后报一个和根因无关的 NoSuchBeanDefinitionException。
 * 这里显式判断、无效值直接抛带原文的异常，排查成本低得多。
 */
@Configuration
public class SeckillBulkheadConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SeckillBulkheadConfiguration.class);

    @Bean
    public SeckillBulkhead seckillBulkhead(
            @Value("${mall.seckill.bulkhead.mode:adaptive}") String mode,
            @Value("${mall.seckill.bulkhead.capacity:32}") int capacity,
            @Value("${mall.seckill.bulkhead.min-limit:4}") int minLimit,
            @Value("${mall.seckill.bulkhead.max-concurrency:200}") int maxConcurrency,
            @Value("${mall.seckill.bulkhead.rtt-tolerance:1.5}") double rttTolerance) {

        String normalized = mode == null ? "" : mode.trim().toLowerCase();
        switch (normalized) {
            case "adaptive" -> {
                log.info("秒杀闸门: 自适应模式（Gradient2）initialLimit={} minLimit={} "
                        + "maxConcurrency={} rttTolerance={}", capacity, minLimit, maxConcurrency, rttTolerance);
                return new AdaptiveSeckillBulkhead(capacity, minLimit, maxConcurrency, rttTolerance);
            }
            case "static" -> {
                log.info("秒杀闸门: 静态模式，容量={}", capacity);
                return new StaticSeckillBulkhead(capacity);
            }
            default -> throw new IllegalArgumentException(
                    "mall.seckill.bulkhead.mode 只能是 adaptive 或 static，实际配的是: " + mode);
        }
    }
}

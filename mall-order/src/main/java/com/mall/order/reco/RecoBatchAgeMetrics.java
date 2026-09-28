package com.mall.order.reco;

import com.mall.order.dao.RecoActiveDao;
import com.mall.order.entity.RecoActiveEntity;
import com.mall.order.reco.batch.BatchConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 「搭配购买」线上在用的批次有多旧：{@code mall_reco_active_batch_age_seconds{kind="complement"}}。
 *
 * <h3>为什么盯「数据多旧」而不是「Job 成没成功」</h3>
 * 集群没装 kube-state-metrics，Prometheus 看不到 Job 状态。但就算装了，Job 状态也只是过程：
 * CronJob 被 suspend、调度没触发、镜像拉不下来、闸门拒绝、Job 失败 —— 这些最后都表现成
 * 「指针很久没切」。盯这一个结果，所有失败方式都跑不掉，包括还没想到的那种。
 *
 * <h3>三个取值</h3>
 * <ul>
 *   <li>NaN：进程起来后还没读到过（启动头几秒）。{@code NaN > x} 恒为假，不会误报。</li>
 *   <li>+Inf：读到了，但根本没有生效批次。同一条「批次过旧」告警会触发。</li>
 *   <li>其余：现在 − 指针切换时间。<b>每次抓取现算</b>，所以刷新失败时年龄照样往上走 ——
 *       读库失败保留上一次的切换时间，而不是归零；归零等于把问题藏起来。</li>
 * </ul>
 *
 * <h3>为什么定时刷新，不在 gauge 回调里查库</h3>
 * gauge 回调跑在 Prometheus 抓取线程上：每 15 秒 × 每个副本打一次库不值，更糟的是库一卡，
 * {@code /actuator/prometheus} 整个跟着卡，这个服务的所有指标一起断。定时刷新把两者隔开。
 */
@Component
public class RecoBatchAgeMetrics {

    private static final Logger log = LoggerFactory.getLogger(RecoBatchAgeMetrics.class);
    static final long NEVER_READ = Long.MIN_VALUE;
    static final long NO_ACTIVE = -1L;

    private final RecoActiveDao activeDao;
    private final Clock clock;
    /** 指针切换时间（epoch ms）；NEVER_READ / NO_ACTIVE 见上 */
    private final AtomicLong switchedAtMs = new AtomicLong(NEVER_READ);

    @Autowired
    public RecoBatchAgeMetrics(RecoActiveDao activeDao, MeterRegistry registry) {
        this(activeDao, registry, Clock.systemUTC());
    }

    RecoBatchAgeMetrics(RecoActiveDao activeDao, MeterRegistry registry, Clock clock) {
        this.activeDao = activeDao;
        this.clock = clock;
        Gauge.builder("mall.reco.active.batch.age.seconds", this, RecoBatchAgeMetrics::ageSeconds)
                .tag("kind", BatchConfig.KIND)
                .description("线上在用的「搭配购买」批次距指针切换过了多少秒；没有生效批次为 +Inf。持续上涨说明批任务没在产出")
                .register(registry);
    }

    @Scheduled(initialDelayString = "${mall.order.reco.age-refresh-initial-delay-ms:5000}",
            fixedDelayString = "${mall.order.reco.age-refresh-ms:60000}")
    public void refresh() {
        try {
            RecoActiveEntity a = activeDao.selectById(BatchConfig.KIND);
            switchedAtMs.set(a == null || a.getSwitchedAt() == null ? NO_ACTIVE : a.getSwitchedAt().getTime());
        } catch (RuntimeException e) {
            // 保留上一次的值：年龄继续涨，真出问题时告警照常响
            log.warn("读取「搭配购买」生效批次失败，批次年龄指标沿用上一次的切换时间: {}", e.toString());
        }
    }

    double ageSeconds() {
        long at = switchedAtMs.get();
        if (at == NEVER_READ) {
            return Double.NaN;
        }
        if (at == NO_ACTIVE) {
            return Double.POSITIVE_INFINITY;
        }
        return (clock.millis() - at) / 1000.0;
    }
}

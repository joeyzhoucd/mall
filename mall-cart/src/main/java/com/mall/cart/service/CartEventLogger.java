package com.mall.cart.service;

import com.mall.cart.feign.MemberCartLogFeignService;
import com.mall.cart.to.CartLogTo;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * 购物车行为埋点的投递器：入队 → 批量 → 投给 mall-member 落库。
 *
 * <h3>三条硬约束，顺序就是优先级</h3>
 * <ol>
 *   <li><b>绝不拖慢加购</b>。{@link #record} 只做一次
 *       {@link BlockingQueue#offer}（非阻塞），队列满了直接丢。
 *       用户路径上不会有任何 HTTP 调用、不会有锁等待。</li>
 *   <li><b>绝不让埋点失败变成加购失败</b>。record 里吞掉一切异常。</li>
 *   <li><b>绝不无限堆积</b>。队列有界；满了丢最新的，并计数。
 *       无界队列在下游长时间不可用时会把堆打爆 —— 那是埋点搞垮主服务，
 *       比丢埋点严重得多。</li>
 * </ol>
 *
 * <h3>为什么批量而不是一条一发</h3>
 * 十万单的数据生成里加购事件是四十五万级别的。一条一个 HTTP 请求，
 * 光往返就能把 mall-member 压住。攒批之后请求数降两个数量级。
 *
 * <h3>为什么不用 MQ</h3>
 * mall-cart 的 pom 里没有 amqp，而且它是刻意保持轻量的（连 mybatis/mysql 都排除了）。
 * 为了一条可丢的分析信号给它加 broker 依赖、加队列和消费者，代价大于收益。
 * <b>如果这个信号哪天变成业务关键（比如参与算钱或风控），必须换成 MQ</b> ——
 * 那时要的是「不丢」，而这里的设计前提恰恰是「可以丢」。
 */
@Slf4j
@Component
public class CartEventLogger {

    /** 队列容量。按 500/s 的埋点速率算，够缓冲 20 秒的下游不可用。 */
    private static final int QUEUE_CAPACITY = 10000;
    /** 单批上限，要和 mall-member 那边的 MAX_BATCH 对齐。 */
    private static final int MAX_BATCH = 500;

    private final BlockingQueue<CartLogTo> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    @Autowired
    private MemberCartLogFeignService feign;

    private Counter accepted;
    private Counter dropped;
    private Counter failed;

    @Value("${mall.cart.event-log.enabled:true}")
    private boolean enabled;

    @Autowired
    public CartEventLogger(MeterRegistry registry) {
        // 指标是这套东西唯一的可观测入口：丢弃和失败都不影响业务，
        // 没有指标的话它默默不工作也没人知道。
        this.accepted = Counter.builder("mall_cart_event_log").tag("result", "accepted").register(registry);
        this.dropped = Counter.builder("mall_cart_event_log").tag("result", "dropped").register(registry);
        this.failed = Counter.builder("mall_cart_event_log").tag("result", "failed").register(registry);
    }

    /**
     * 记录一条行为。<b>这个方法在用户请求线程上跑，必须是纯入队、非阻塞、不抛异常。</b>
     */
    public void record(Long memberId, Long skuId, Long spuId, int action, Integer quantity) {
        if (!enabled) {
            return;
        }
        try {
            if (skuId == null || spuId == null) {
                // spuId 为 null 说明是老的缓存购物车项（加 spuId 字段之前存的）。
                // 这种条目没法参与 SPU 粒度的共现统计，记了也是脏数据，直接丢。
                return;
            }
            CartLogTo to = new CartLogTo();
            to.setMemberId(memberId == null ? 0L : memberId);
            to.setSkuId(skuId);
            to.setSpuId(spuId);
            to.setAction(action);
            to.setQuantity(quantity);
            to.setCreateTime(new Date());
            if (queue.offer(to)) {
                accepted.increment();
            } else {
                dropped.increment();
            }
        } catch (Exception e) {
            // 埋点的任何异常都不能冒泡到加购
            dropped.increment();
        }
    }

    /** 定时刷。500ms 一次，配合 MAX_BATCH 既控住延迟也控住请求数。 */
    @Scheduled(fixedDelay = 500)
    public void flush() {
        drainAndSend();
    }

    /**
     * 停机前把队列里剩下的发出去。
     * <p>优雅下线有 12 秒排空窗口（见 values.yaml 的 gracefulShutdown），
     * 这里的一两批发得完。发不完也只是丢埋点，不阻塞关闭。
     */
    @PreDestroy
    public void drainOnShutdown() {
        for (int i = 0; i < 5 && !queue.isEmpty(); i++) {
            drainAndSend();
        }
    }

    private void drainAndSend() {
        if (queue.isEmpty()) {
            return;
        }
        List<CartLogTo> batch = new ArrayList<>(MAX_BATCH);
        queue.drainTo(batch, MAX_BATCH);
        if (batch.isEmpty()) {
            return;
        }
        try {
            feign.batch(batch);
        } catch (Exception e) {
            // 【刻意不重投】重投要么放回队列（可能挤掉新事件、且下游持续不可用时会死循环），
            // 要么本地落盘（那就是在造第二套存储）。埋点是可丢的，丢了记指标就够。
            failed.increment(batch.size());
            log.warn("购物车埋点投递失败，丢弃 {} 条：{}", batch.size(), e.toString());
        }
    }
}

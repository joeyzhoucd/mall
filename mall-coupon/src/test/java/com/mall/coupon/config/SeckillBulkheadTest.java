package com.mall.coupon.config;

import com.netflix.concurrency.limits.limit.FixedLimit;
import com.netflix.concurrency.limits.limit.SettableLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SeckillBulkhead} 的行为契约测试，<b>两个实现跑同一套断言</b>。
 * <p>
 * 纯内存逻辑，不需要任何中间件，所以【没有】打 integration 标签，本地和 CI 都会跑。
 *
 * <h3>为什么参数化到两个实现上</h3>
 * {@link StaticSeckillBulkhead} 和 {@link AdaptiveSeckillBulkhead} 的内部机制完全不同
 * （信号量 vs Netflix limiter），但对外的契约必须一致：容量内放行、超出立即拒绝、
 * 任何路径都归还许可。只测其中一个，切换 {@code mall.seckill.bulkhead.mode} 之后
 * 才发现另一个行为不同 —— 那种问题会在生产里以「切了模式就出怪事」的形式出现，
 * 极难定位。
 * <p>
 * 自适应实现在契约测试里注入 {@link FixedLimit}：这样限额在测试期间不会变动，
 * 两个实现才真正跑在同一套断言下。<b>如果用它默认的 Gradient2，限额会随 RTT 样本
 * 自己漂移，「结束后许可应等于容量」这类断言就会偶发失败</b> ——
 * 那会变成大家习惯性重跑的噪声用例，反而掩盖真问题。
 * 自适应<b>特有</b>的行为（限额变了闸门就跟着变）单独一条用例测，见文件末尾。
 *
 * <h3>这里最要紧的一条</h3>
 * "异常路径也必须归还许可"：漏归还的话许可只减不增，闸门会越关越小，
 * 最终所有秒杀请求都被拒绝 —— 而且不可逆，只能重启 pod。
 * 这种 bug 在正常路径的测试里完全看不出来，只有真的抛异常时才显形。
 */
class SeckillBulkheadTest {

    /** 两个实现的工厂。容量 -> 实例。 */
    static Stream<Arguments> implementations() {
        return Stream.of(
                Arguments.of("static", (IntFunction<SeckillBulkhead>) StaticSeckillBulkhead::new),
                Arguments.of("adaptive(FixedLimit)",
                        (IntFunction<SeckillBulkhead>) c -> new AdaptiveSeckillBulkhead(FixedLimit.of(c))));
    }

    @ParameterizedTest(name = "[{0}] 容量以内正常放行")
    @MethodSource("implementations")
    void allowsWithinCapacity(String name, IntFunction<SeckillBulkhead> factory) {
        SeckillBulkhead bulkhead = factory.apply(2);
        assertEquals("ok", bulkhead.call(() -> "ok", () -> "busy"));
        assertEquals(bulkhead.capacity(), bulkhead.availablePermits(), "调用结束后许可应全部归还");
        assertEquals(0, bulkhead.rejectedCount());
    }

    @ParameterizedTest(name = "[{0}] 超出容量时立即拒绝，不排队等待")
    @MethodSource("implementations")
    void rejectsBeyondCapacity(String name, IntFunction<SeckillBulkhead> factory) throws Exception {
        int capacity = 3;
        SeckillBulkhead bulkhead = factory.apply(capacity);

        // 先用 capacity 个线程把许可全部占住并卡在里面
        CountDownLatch occupied = new CountDownLatch(capacity);
        CountDownLatch release = new CountDownLatch(1);
        List<Thread> holders = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            Thread t = new Thread(() -> bulkhead.call(() -> {
                occupied.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "held";
            }, () -> "busy"));
            t.start();
            holders.add(t);
        }
        assertTrue(occupied.await(5, TimeUnit.SECONDS), "占位线程没能在 5 秒内全部进入闸门");
        assertEquals(0, bulkhead.availablePermits());

        // 此时再来一个请求，必须【立即】拿到拒绝结果，而不是阻塞等待
        long start = System.nanoTime();
        String result = bulkhead.call(() -> "should-not-run", () -> "busy");
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertEquals("busy", result, "超出容量时应返回拒绝结果");
        // 阈值给得很宽（1 秒）：这里要证明的是"没有排队等待"这个语义，
        // 而不是测量性能。设得太紧会让这条用例在 CI 上偶发失败，
        // 变成大家习惯性重跑的噪声用例，反而失去意义。
        assertTrue(elapsedMillis < 1000, "拒绝应立即返回，实际耗时 " + elapsedMillis + "ms");
        assertEquals(1, bulkhead.rejectedCount());

        release.countDown();
        for (Thread t : holders) {
            t.join(5000);
        }
        assertEquals(capacity, bulkhead.availablePermits(), "占位线程结束后许可应全部归还");
    }

    @ParameterizedTest(name = "[{0}] 业务抛异常时也必须归还许可")
    @MethodSource("implementations")
    void releasesPermitOnException(String name, IntFunction<SeckillBulkhead> factory) {
        SeckillBulkhead bulkhead = factory.apply(1);

        assertThrows(IllegalStateException.class, () -> bulkhead.call(
                () -> { throw new IllegalStateException("业务炸了"); },
                () -> "busy"));

        // 这一条是整个类里最重要的断言：如果 call() 里没有用 finally 归还，
        // 这里会是 0，闸门从此永久关闭。
        assertEquals(1, bulkhead.availablePermits(),
                "异常路径没有归还许可 —— 闸门会越关越小直到全部拒绝，且只能重启恢复");

        // 归还之后还能正常放行，确认闸门没有被那次异常"卡死"
        assertEquals("ok", bulkhead.call(() -> "ok", () -> "busy"));
    }

    @ParameterizedTest(name = "[{0}] 并发压力下许可数守恒，不泄漏也不超发")
    @MethodSource("implementations")
    void permitsAreConservedUnderConcurrency(String name, IntFunction<SeckillBulkhead> factory) throws Exception {
        int capacity = 8;
        int threads = 64;
        SeckillBulkhead bulkhead = factory.apply(capacity);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            // 一半的任务故意抛异常，把正常路径和异常路径混在一起压，
            // 这样"异常路径漏归还"这种 bug 在并发下也会被许可守恒的断言抓到。
            boolean shouldThrow = i % 2 == 0;
            new Thread(() -> {
                try {
                    start.await();
                    try {
                        bulkhead.call(() -> {
                            int now = inFlight.incrementAndGet();
                            maxObserved.accumulateAndGet(now, Math::max);
                            accepted.incrementAndGet();
                            try {
                                Thread.sleep(2);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            inFlight.decrementAndGet();
                            if (shouldThrow) {
                                throw new IllegalStateException("boom");
                            }
                            return "ok";
                        }, () -> "busy");
                    } catch (IllegalStateException ignored) {
                        // 预期内
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "并发用例未在 30 秒内结束");

        assertTrue(maxObserved.get() <= capacity,
                "同时在途数超过了容量：观察到 " + maxObserved.get() + "，容量 " + capacity);
        assertEquals(capacity, bulkhead.availablePermits(), "结束后许可未全部归还，存在泄漏");
        assertEquals(threads, accepted.get() + bulkhead.rejectedCount(),
                "放行数加拒绝数应等于总请求数，对不上说明有请求既没执行也没被计入拒绝");
    }

    /**
     * 自适应实现特有的契约：限额变了，闸门的实际放行数就要跟着变。
     * <p>
     * 用 {@link SettableLimit} 而不是默认的 Gradient2：后者的限额变化取决于 RTT 窗口，
     * 单元测试里无法确定性触发，只能靠制造延迟去"撞"算法 —— 那样的用例偶发失败，
     * 比没有测试更糟。这里要验证的本来也不是 Gradient2 的算法（那是库的责任），
     * 而是<b>我们这一层有没有把限额变化正确传导下去</b>：
     * {@code capacity()} 是否跟着走、内部信号量是否真的跟着扩缩。
     */
    @Test
    @DisplayName("自适应实现：限额变化会传导到实际放行数上")
    void adaptiveLimitChangePropagates() throws Exception {
        SettableLimit limit = SettableLimit.startingAt(1);
        AdaptiveSeckillBulkhead bulkhead = new AdaptiveSeckillBulkhead(limit);

        assertEquals(1, bulkhead.capacity());

        // 占住唯一的那个许可
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> bulkhead.call(() -> {
            occupied.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "held";
        }, () -> "busy"));
        holder.start();
        assertTrue(occupied.await(5, TimeUnit.SECONDS));

        // 限额是 1 且已被占满 -> 拒绝
        assertEquals("busy", bulkhead.call(() -> "ok", () -> "busy"));

        // 把限额调到 3，同一时刻应该立刻能再放进来
        limit.setLimit(3);
        assertEquals(3, bulkhead.capacity(), "capacity() 没有跟随限额变化");
        assertEquals("ok", bulkhead.call(() -> "ok", () -> "busy"),
                "限额调大后仍被拒绝 —— 说明限额变化没有传导到内部的许可数上");

        // 再调回 1，应该重新拒绝
        limit.setLimit(1);
        assertEquals("busy", bulkhead.call(() -> "ok", () -> "busy"),
                "限额调小后仍放行 —— 收缩没有生效，过载时闸门不会真的关小");

        release.countDown();
        holder.join(5000);
    }
}

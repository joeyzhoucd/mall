package com.mall.coupon.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SeckillBulkhead} 的行为测试。
 * <p>
 * 纯内存逻辑，不需要任何中间件，所以【没有】打 integration 标签，本地和 CI 都会跑。
 * <p>
 * 这里最要紧的是"异常路径也必须释放许可"那一条：漏释放的话许可只减不增，
 * 闸门会越关越小，最终所有秒杀请求都被拒绝 —— 而且不可逆，只能重启 pod。
 * 这种 bug 在正常路径的测试里完全看不出来，只有真的抛异常时才显形，
 * 所以必须单独有一条用例守着。
 */
class SeckillBulkheadTest {

    @Test
    @DisplayName("容量以内正常放行，返回业务结果")
    void allowsWithinCapacity() {
        SeckillBulkhead bulkhead = new SeckillBulkhead(2);
        assertEquals("ok", bulkhead.call(() -> "ok", () -> "busy"));
        assertEquals(2, bulkhead.availablePermits(), "调用结束后许可应全部归还");
        assertEquals(0, bulkhead.rejectedCount());
    }

    @Test
    @DisplayName("超出容量时立即拒绝，不排队等待")
    void rejectsBeyondCapacity() throws Exception {
        int capacity = 3;
        SeckillBulkhead bulkhead = new SeckillBulkhead(capacity);

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

    @Test
    @DisplayName("业务抛异常时也必须归还许可")
    void releasesPermitOnException() {
        SeckillBulkhead bulkhead = new SeckillBulkhead(1);

        assertThrows(IllegalStateException.class, () -> bulkhead.call(
                () -> { throw new IllegalStateException("业务炸了"); },
                () -> "busy"));

        // 这一条是整个类里最重要的断言：如果 call() 里没有用 finally 释放，
        // 这里会是 0，闸门从此永久关闭。
        assertEquals(1, bulkhead.availablePermits(),
                "异常路径没有归还许可 —— 闸门会越关越小直到全部拒绝，且只能重启恢复");

        // 归还之后还能正常放行，确认闸门没有被那次异常"卡死"
        assertEquals("ok", bulkhead.call(() -> "ok", () -> "busy"));
    }

    @Test
    @DisplayName("并发压力下许可数守恒，不会泄漏也不会超发")
    void permitsAreConservedUnderConcurrency() throws Exception {
        int capacity = 8;
        int threads = 64;
        SeckillBulkhead bulkhead = new SeckillBulkhead(capacity);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        AtomicInteger accepted = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            // 一半的任务故意抛异常，把正常路径和异常路径混在一起压，
            // 这样"异常路径漏释放"这种 bug 在并发下也会被许可守恒的断言抓到。
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
}

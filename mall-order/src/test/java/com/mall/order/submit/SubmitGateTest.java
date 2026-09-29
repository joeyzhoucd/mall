package com.mall.order.submit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmitGateTest {

    @Test
    void admitsUpToTheLimitThenRejectsImmediately() {
        SubmitGate gate = new SubmitGate(2);
        assertThat(gate.tryEnter()).isTrue();
        assertThat(gate.tryEnter()).isTrue();
        assertThat(gate.tryEnter()).isFalse();
        assertThat(gate.rejectedCount()).isEqualTo(1);
        gate.exit();
        assertThat(gate.tryEnter()).as("归还之后又能进").isTrue();
    }

    @Test
    void loweringTheLimitLetsInFlightFinishButStopsNewOnes() {
        SubmitGate gate = new SubmitGate(3);
        gate.tryEnter();
        gate.tryEnter();
        gate.tryEnter();
        gate.setLimit(1);
        assertThat(gate.inFlight()).isEqualTo(3);
        assertThat(gate.tryEnter()).isFalse();
        gate.exit();
        gate.exit();
        assertThat(gate.tryEnter()).as("在途 1 = 新上限，仍不能进").isFalse();
        gate.exit();
        assertThat(gate.tryEnter()).isTrue();
    }

    @Test
    void raisingTheLimitTakesEffectAtOnce() {
        SubmitGate gate = new SubmitGate(1);
        gate.tryEnter();
        assertThat(gate.tryEnter()).isFalse();
        gate.setLimit(2);
        assertThat(gate.tryEnter()).isTrue();
    }

    @Test
    void rejectsNonsenseLimits() {
        assertThatThrownBy(() -> new SubmitGate(0)).isInstanceOf(IllegalArgumentException.class);
        SubmitGate gate = new SubmitGate(5);
        assertThatThrownBy(() -> gate.setLimit(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(gate.limit()).isEqualTo(5);
    }

    /** 64 个线程同时抢 5 个名额：任何时刻在途都不超过 5（CAS 循环没有超卖） */
    @Test
    void neverExceedsTheLimitUnderContention() throws Exception {
        SubmitGate gate = new SubmitGate(5);
        AtomicInteger maxSeen = new AtomicInteger();
        AtomicInteger admitted = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(64);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 64; i++) {
            pool.submit(() -> {
                start.await();
                for (int k = 0; k < 2000; k++) {
                    if (gate.tryEnter()) {
                        try {
                            admitted.incrementAndGet();
                            maxSeen.accumulateAndGet(gate.inFlight(), Math::max);
                        } finally {
                            gate.exit();
                        }
                    }
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(maxSeen.get()).isBetween(1, 5);
        assertThat(gate.inFlight()).as("全部归还").isZero();
        assertThat(admitted.get() + gate.rejectedCount()).isEqualTo(64L * 2000);
    }
}

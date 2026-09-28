package com.mall.order.reco;

import com.mall.order.dao.RecoActiveDao;
import com.mall.order.entity.RecoActiveEntity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 批次年龄指标：没读过不误报、没批次必报、读库失败时年龄继续涨而不是归零 */
class RecoBatchAgeMetricsTest {

    private static final long T0 = 1_790_000_000_000L;

    private final AtomicLong now = new AtomicLong(T0);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(now.get());
        }
    };
    private RecoActiveDao dao;
    private SimpleMeterRegistry registry;
    private RecoBatchAgeMetrics metrics;

    @BeforeEach
    void setUp() {
        dao = mock(RecoActiveDao.class);
        registry = new SimpleMeterRegistry();
        metrics = new RecoBatchAgeMetrics(dao, registry, clock);
    }

    private double gauge() {
        return registry.get("mall.reco.active.batch.age.seconds").tag("kind", "complement").gauge().value();
    }

    private static RecoActiveEntity active(long switchedAtMs) {
        RecoActiveEntity a = new RecoActiveEntity();
        a.setKind("complement");
        a.setBatchId(5L);
        a.setSwitchedAt(new Date(switchedAtMs));
        return a;
    }

    @Test
    void beforeFirstReadIsNaNSoNothingFires() {
        assertThat(gauge()).isNaN();
    }

    @Test
    void ageIsComputedAtScrapeTime() {
        when(dao.selectById("complement")).thenReturn(active(T0 - 3_600_000));
        metrics.refresh();
        assertThat(gauge()).isEqualTo(3600.0);
        now.addAndGet(90_000);                       // 不再刷新，抓取时现算
        assertThat(gauge()).isEqualTo(3690.0);
    }

    @Test
    void noActiveBatchIsInfinite() {
        when(dao.selectById("complement")).thenReturn(null);
        metrics.refresh();
        assertThat(gauge()).isInfinite().isPositive();
    }

    @Test
    void readFailureKeepsLastSwitchTimeSoAgeKeepsGrowing() {
        when(dao.selectById("complement")).thenReturn(active(T0));
        metrics.refresh();
        when(dao.selectById("complement")).thenThrow(new RuntimeException("mysql 不可达"));
        now.addAndGet(40L * 3600 * 1000);
        metrics.refresh();
        assertThat(gauge()).isEqualTo(40.0 * 3600);
    }

    @Test
    void newSwitchResetsAge() {
        when(dao.selectById("complement")).thenReturn(active(T0 - 100L * 3600 * 1000));
        metrics.refresh();
        assertThat(gauge()).isEqualTo(100.0 * 3600);
        when(dao.selectById("complement")).thenReturn(active(T0 - 1000));
        metrics.refresh();
        assertThat(gauge()).isEqualTo(1.0);
    }
}

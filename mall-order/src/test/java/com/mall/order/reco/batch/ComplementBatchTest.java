package com.mall.order.reco.batch;

import com.mall.order.reco.ComplementCalculator.Candidate;
import com.mall.order.reco.batch.BatchStore.BatchStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplementBatchTest {

    /** 内存版存储：只记录流程关心的东西（批次状态、指针、写了几行） */
    static final class FakeStore implements BatchStore {
        BasketAccumulator.Baskets baskets;
        final Map<Long, BatchStatus> status = new HashMap<>();
        final Map<Long, Integer> coverage = new HashMap<>();
        final Map<Long, String> reason = new HashMap<>();
        Long active;
        long nextId = 100;
        boolean failOnWrite;

        @Override
        public BasketAccumulator.Baskets loadBaskets(BatchConfig c) {
            return baskets;
        }

        @Override
        public OptionalInt activeCoverage(String kind) {
            return active == null ? OptionalInt.empty() : OptionalInt.of(coverage.get(active));
        }

        @Override
        public long createBatch(String kind, String params) {
            long id = nextId++;
            status.put(id, BatchStatus.RUNNING);
            return id;
        }

        @Override
        public int writeRows(long batchId, Map<Long, List<Candidate>> result) {
            if (failOnWrite) {
                throw new IllegalStateException("模拟写入失败");
            }
            return result.values().stream().mapToInt(List::size).sum();
        }

        @Override
        public void finish(long batchId, BatchStatus s, Stats stats, String r) {
            status.put(batchId, s);
            coverage.put(batchId, stats.spusCovered());
            reason.put(batchId, r);
        }

        @Override
        public void activate(String kind, long batchId) {
            if (active != null) {
                status.put(active, BatchStatus.SUPERSEDED);
            }
            status.put(batchId, BatchStatus.ACTIVE);
            active = batchId;
        }

        @Override
        public int prune(String kind, int keep) {
            return 0;
        }

        /** 预置一个已生效的旧批次 */
        long seedActive(int covered) {
            long id = nextId++;
            status.put(id, BatchStatus.ACTIVE);
            coverage.put(id, covered);
            active = id;
            return id;
        }
    }

    private static BatchConfig config(boolean activate) {
        return new BatchConfig("jdbc:x", "u", "p", List.of(1, 2, 3, 5), null, null, 20, false, activate, 0.5, 3);
    }

    /** n 个 SPU 两两成对（1-2、3-4…），每对一起出现 5 单，再加噪声单撑出总单数 → 覆盖 n 个 SPU */
    private static BasketAccumulator.Baskets basketsCovering(int n) {
        List<long[]> list = new ArrayList<>();
        for (int s = 1; s + 1 <= n; s += 2) {
            for (int k = 0; k < 5; k++) {
                list.add(new long[]{s, s + 1});
            }
        }
        for (int i = 0; i < n * 5; i++) {
            list.add(new long[]{100_000 + i});
        }
        return new BasketAccumulator.Baskets(list, Map.of(), list.size(), 0);
    }

    private static ComplementBatch batch(FakeStore s) {
        return new ComplementBatch(s, () -> 0L);
    }

    @Test
    void firstBatchActivatesWhenThereIsNothingToCompareWith() throws Exception {
        FakeStore s = new FakeStore();
        s.baskets = basketsCovering(40);
        ComplementBatch.Result r = batch(s).run(config(true));
        assertThat(r.status()).isEqualTo(BatchStatus.ACTIVE);
        assertThat(s.active).isEqualTo(r.batchId());
        assertThat(r.stats().spusCovered()).isEqualTo(40);
    }

    @Test
    void zeroOrdersIsRejectedAndThePointerStays() throws Exception {
        FakeStore s = new FakeStore();
        long old = s.seedActive(40);
        s.baskets = new BasketAccumulator.Baskets(List.of(), Map.of(), 0, 0);
        ComplementBatch.Result r = batch(s).run(config(true));
        assertThat(r.status()).isEqualTo(BatchStatus.REJECTED);
        assertThat(r.reason()).contains("0 单");
        assertThat(s.active).isEqualTo(old);
        assertThat(s.status.get(old)).isEqualTo(BatchStatus.ACTIVE);
    }

    @Test
    void coverageCollapseIsRejectedAndThePointerStays() throws Exception {
        FakeStore s = new FakeStore();
        long old = s.seedActive(1000);
        s.baskets = basketsCovering(40);      // 40 < 1000 × 0.5
        ComplementBatch.Result r = batch(s).run(config(true));
        assertThat(r.status()).isEqualTo(BatchStatus.REJECTED);
        assertThat(r.reason()).contains("40").contains("1000");
        assertThat(s.active).isEqualTo(old);
    }

    @Test
    void moderateDropAboveTheRatioStillActivates() throws Exception {
        FakeStore s = new FakeStore();
        long old = s.seedActive(60);
        s.baskets = basketsCovering(40);      // 40 >= 60 × 0.5
        ComplementBatch.Result r = batch(s).run(config(true));
        assertThat(r.status()).isEqualTo(BatchStatus.ACTIVE);
        assertThat(s.active).isEqualTo(r.batchId());
        assertThat(s.status.get(old)).isEqualTo(BatchStatus.SUPERSEDED);
    }

    @Test
    void writeFailureMarksFailedAndThePointerStays() throws Exception {
        FakeStore s = new FakeStore();
        long old = s.seedActive(40);
        s.baskets = basketsCovering(40);
        s.failOnWrite = true;
        ComplementBatch.Result r = batch(s).run(config(true));
        assertThat(r.status()).isEqualTo(BatchStatus.FAILED);
        assertThat(s.status.get(r.batchId())).isEqualTo(BatchStatus.FAILED);
        assertThat(s.reason.get(r.batchId())).contains("模拟写入失败");
        assertThat(s.active).isEqualTo(old);
    }

    @Test
    void evaluationModeLeavesAReadyBatchWithoutSwitching() throws Exception {
        FakeStore s = new FakeStore();
        long old = s.seedActive(40);
        s.baskets = basketsCovering(40);
        ComplementBatch.Result r = batch(s).run(config(false));
        assertThat(r.status()).isEqualTo(BatchStatus.READY);
        assertThat(s.active).isEqualTo(old);
    }

    // ------------------------------------------------------------------ 拼篮子

    @Test
    void accumulatorGroupsByOrderAndSkipsMissingSpu() {
        BasketAccumulator a = new BasketAccumulator();
        a.add(1, 10L, 7L);
        a.add(1, 11L, 8L);
        a.add(2, null, null);     // 旧订单：spu_id 从来没写过
        a.add(2, 0L, null);
        a.add(3, 12L, 7L);
        BasketAccumulator.Baskets b = a.finish();
        assertThat(b.orders()).isEqualTo(2);          // 订单 2 所有行都没 spu，不成篮子
        assertThat(b.baskets()).containsExactly(new long[]{10, 11}, new long[]{12});
        assertThat(b.skippedRows()).isEqualTo(2);
        assertThat(b.categoryOf()).containsEntry(10L, 7L).containsEntry(11L, 8L);
    }

    @Test
    void accumulatorRefusesUnsortedRows() {
        BasketAccumulator a = new BasketAccumulator();
        a.add(5, 1L, null);
        assertThatThrownBy(() -> a.add(3, 2L, null)).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ 配置

    @Test
    void productionDefaults() {
        BatchConfig c = BatchConfig.fromEnv(Map.of("MYSQL_HOST", "mysql"));
        assertThat(c.orderStatuses()).containsExactly(1, 2, 3, 5);   // 付过款且没退款
        assertThat(c.excludeSameCategory()).isTrue();
        assertThat(c.activate()).isTrue();
        assertThat(c.topK()).isEqualTo(20);
        assertThat(c.jdbcUrl()).contains("//mysql:3306/mall_oms").contains("sslMode=REQUIRED").contains("rewriteBatchedStatements=true");
        assertThat(c.describe()).doesNotContain("root");   // 参数会写进表，不能带账号密码
    }

    @Test
    void evaluationModeFromEnv() {
        BatchConfig c = BatchConfig.fromEnv(Map.of("RECO_ORDER_STATUSES", "all", "RECO_MEMBER_FROM", "8110001",
                "RECO_MEMBER_TO", "8112000", "RECO_EXCLUDE_SAME_CATEGORY", "false", "RECO_ACTIVATE", "false"));
        assertThat(c.orderStatuses()).isEmpty();
        assertThat(c.memberFrom()).isEqualTo(8110001L);
        assertThat(c.activate()).isFalse();
    }

    @Test
    void halfAMemberRangeIsRejected() {
        assertThatThrownBy(() -> BatchConfig.fromEnv(Map.of("RECO_MEMBER_FROM", "1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

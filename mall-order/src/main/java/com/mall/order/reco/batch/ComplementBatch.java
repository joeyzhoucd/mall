package com.mall.order.reco.batch;

import com.mall.order.reco.ComplementCalculator;
import com.mall.order.reco.ComplementCalculator.Candidate;
import com.mall.order.reco.batch.BatchStore.BatchStatus;
import com.mall.order.reco.batch.BatchStore.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

/**
 * 一次「搭配购买」批算：读篮子 → LLR → 写新批次 → 质量闸门 → 切指针 → 清理旧批次。
 *
 * <h3>质量闸门（任一不过就不切指针，线上继续用上一批）</h3>
 * <ol>
 *   <li>读到 0 单。库连错、SQL 条件写错、权限变了，表现都是「查询成功、0 行」——
 *       直接覆盖的话线上推荐被静默清空，批任务还报成功。</li>
 *   <li>覆盖的 SPU 数低于当前生效批次 × minCoverageRatio（默认一半）。
 *       真实业务里覆盖不会一夜腰斩；腰斩说明读数据那一步出了问题。
 *       第一批（还没有生效批次）没有比较基准，只要求覆盖 &gt; 0。</li>
 * </ol>
 * 任何异常 → FAILED，同样不动指针。明细先写、指针后切，所以写到一半挂了也不会有人读到半批。
 */
public final class ComplementBatch {

    private static final Logger log = LoggerFactory.getLogger(ComplementBatch.class);

    public record Result(long batchId, BatchStatus status, Stats stats, String reason) {
    }

    private final BatchStore store;
    private final LongSupplier clock;

    public ComplementBatch(BatchStore store, LongSupplier clockMillis) {
        this.store = store;
        this.clock = clockMillis;
    }

    public Result run(BatchConfig config) throws Exception {
        long t0 = clock.getAsLong();
        String kind = BatchConfig.KIND;
        long batchId = store.createBatch(kind, config.describe());
        Stats stats = new Stats(0, 0, 0, null, 0, 0);
        try {
            BasketAccumulator.Baskets b = store.loadBaskets(config);
            OptionalInt prev = store.activeCoverage(kind);
            Integer prevCov = prev.isPresent() ? prev.getAsInt() : null;
            log.info("批次 {}：读到 {} 单（跳过 spu_id 为空的行 {}），当前生效批次覆盖 {}", batchId, b.orders(), b.skippedRows(), prevCov);

            if (b.orders() == 0) {
                stats = new Stats(0, 0, 0, prevCov, b.skippedRows(), clock.getAsLong() - t0);
                return reject(batchId, stats, "读到 0 单（" + config.describe() + "）");
            }

            Map<Long, List<Candidate>> result = ComplementCalculator.compute(b.baskets(), b.categoryOf(),
                    new ComplementCalculator.Options(config.topK(), config.excludeSameCategory()));
            int covered = result.size();
            int rows = store.writeRows(batchId, result);
            stats = new Stats(b.orders(), covered, rows, prevCov, b.skippedRows(), clock.getAsLong() - t0);

            if (covered == 0) {
                return reject(batchId, stats, "有订单但 0 个 SPU 有搭配");
            }
            if (prevCov != null && covered < prevCov * config.minCoverageRatio()) {
                return reject(batchId, stats, "覆盖 " + covered + " 低于生效批次 " + prevCov + " × " + config.minCoverageRatio());
            }

            if (!config.activate()) {
                store.finish(batchId, BatchStatus.READY, stats, null);
                store.prune(kind, config.keepBatches());
                log.info("批次 {} READY（未启用）：覆盖 {} 个 SPU、{} 行，用时 {}ms", batchId, covered, rows, stats.durationMs());
                return new Result(batchId, BatchStatus.READY, stats, null);
            }
            store.finish(batchId, BatchStatus.READY, stats, null);
            store.activate(kind, batchId);
            int pruned = store.prune(kind, config.keepBatches());
            log.info("批次 {} 已启用：覆盖 {} 个 SPU、{} 行，用时 {}ms；清理旧批次明细 {} 个", batchId, covered, rows, stats.durationMs(), pruned);
            return new Result(batchId, BatchStatus.ACTIVE, stats, null);
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + e.getMessage();
            try {
                store.finish(batchId, BatchStatus.FAILED, stats, reason.length() > 500 ? reason.substring(0, 500) : reason);
            } catch (Exception inner) {
                e.addSuppressed(inner);
            }
            log.error("批次 {} 失败，指针不动：{}", batchId, reason, e);
            return new Result(batchId, BatchStatus.FAILED, stats, reason);
        }
    }

    private Result reject(long batchId, Stats stats, String reason) throws Exception {
        store.finish(batchId, BatchStatus.REJECTED, stats, reason);
        log.warn("批次 {} 被质量闸门拒绝，指针不动：{}", batchId, reason);
        return new Result(batchId, BatchStatus.REJECTED, stats, reason);
    }
}

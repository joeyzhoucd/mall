package com.mall.order.reco.batch;

import com.mall.order.reco.ComplementCalculator.Candidate;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** 批任务对存储的全部需求。生产是 {@link JdbcBatchStore}；单测用内存实现验证质量闸门的每条分支。 */
public interface BatchStore {

    BasketAccumulator.Baskets loadBaskets(BatchConfig config) throws Exception;

    /** 当前生效批次的覆盖 SPU 数；还没有生效批次时为空 */
    OptionalInt activeCoverage(String kind) throws Exception;

    long createBatch(String kind, String params) throws Exception;

    int writeRows(long batchId, Map<Long, List<Candidate>> result) throws Exception;

    void finish(long batchId, BatchStatus status, Stats stats, String reason) throws Exception;

    /** 一个事务里：指针切到 batchId、它变 ACTIVE、原来生效的变 SUPERSEDED */
    void activate(String kind, long batchId) throws Exception;

    /** 只保留最近 keep 个成功批次（ACTIVE/SUPERSEDED/READY）的明细；生效批次永远保留。返回删掉明细的批次数 */
    int prune(String kind, int keep) throws Exception;

    record Stats(int ordersRead, int spusCovered, int rowsWritten, Integer prevSpusCovered, long skippedRows, long durationMs) {
    }

    enum BatchStatus { RUNNING, READY, ACTIVE, SUPERSEDED, REJECTED, FAILED }
}

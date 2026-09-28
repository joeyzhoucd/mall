package com.mall.order.reco.batch;

import com.mall.order.reco.ComplementCalculator.Candidate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.StringJoiner;

/**
 * {@link BatchStore} 的 JDBC 实现。表结构见 mall-deploy/data-seed/migration-2026-09-28-reco-complement.sql。
 *
 * <p>批任务是一个不起 Spring 的 main（原因见 {@link ComplementBatchMain}），所以这里是裸 JDBC，
 * 一个连接用到底，不需要连接池。
 */
public final class JdbcBatchStore implements BatchStore {

    private static final int WRITE_BATCH = 2000;
    private final Connection conn;

    public JdbcBatchStore(Connection conn) {
        this.conn = conn;
    }

    @Override
    public BasketAccumulator.Baskets loadBaskets(BatchConfig c) throws Exception {
        StringBuilder sql = new StringBuilder(
                "SELECT o.id, i.spu_id, i.category_id FROM oms_order o JOIN oms_order_item i ON i.order_id = o.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!c.orderStatuses().isEmpty()) {
            StringJoiner in = new StringJoiner(",", " AND o.status IN (", ")");
            for (Integer s : c.orderStatuses()) {
                in.add("?");
                args.add(s);
            }
            sql.append(in);
        }
        if (c.memberFrom() != null) {
            sql.append(" AND o.member_id BETWEEN ? AND ?");
            args.add(c.memberFrom());
            args.add(c.memberTo());
        }
        // ORDER BY 是 BasketAccumulator 的前提（它会校验，乱序直接失败）
        sql.append(" ORDER BY o.id");
        BasketAccumulator acc = new BasketAccumulator();
        // 流式读：MySQL 驱动只有 fetchSize = Integer.MIN_VALUE 才逐行取，否则先把整个结果集读进内存
        try (PreparedStatement ps = conn.prepareStatement(sql.toString(), ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Integer.MIN_VALUE);
            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long spu = rs.getLong(2);
                    Long spuId = rs.wasNull() ? null : spu;
                    long cat = rs.getLong(3);
                    Long catId = rs.wasNull() ? null : cat;
                    acc.add(rs.getLong(1), spuId, catId);
                }
            }
        }
        return acc.finish();
    }

    @Override
    public OptionalInt activeCoverage(String kind) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT b.spus_covered FROM oms_reco_active a JOIN oms_reco_batch b ON b.id = a.batch_id WHERE a.kind = ?")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty();
            }
        }
    }

    @Override
    public long createBatch(String kind, String params) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO oms_reco_batch (kind, status, params, started_at) VALUES (?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, kind);
            ps.setString(2, BatchStatus.RUNNING.name());
            ps.setString(3, params);
            ps.setTimestamp(4, now());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Override
    public int writeRows(long batchId, Map<Long, List<Candidate>> result) throws Exception {
        int rows = 0;
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO oms_spu_complement (batch_id, spu_id, rank_no, complement_spu_id, score, cooccur) VALUES (?, ?, ?, ?, ?, ?)")) {
            int pending = 0;
            for (Map.Entry<Long, List<Candidate>> e : result.entrySet()) {
                int rank = 1;
                for (Candidate c : e.getValue()) {
                    ps.setLong(1, batchId);
                    ps.setLong(2, e.getKey());
                    ps.setInt(3, rank++);
                    ps.setLong(4, c.spuId());
                    ps.setDouble(5, c.score());
                    ps.setInt(6, c.cooccur());
                    ps.addBatch();
                    rows++;
                    if (++pending == WRITE_BATCH) {
                        ps.executeBatch();
                        conn.commit();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                ps.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
        return rows;
    }

    @Override
    public void finish(long batchId, BatchStatus status, Stats s, String reason) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE oms_reco_batch SET status = ?, orders_read = ?, spus_covered = ?, rows_written = ?, "
                        + "prev_spus_covered = ?, skipped_rows = ?, reason = ?, duration_ms = ?, finished_at = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setInt(2, s.ordersRead());
            ps.setInt(3, s.spusCovered());
            ps.setInt(4, s.rowsWritten());
            ps.setObject(5, s.prevSpusCovered());
            ps.setLong(6, s.skippedRows());
            ps.setString(7, reason);
            ps.setLong(8, s.durationMs());
            ps.setTimestamp(9, now());
            ps.setLong(10, batchId);
            ps.executeUpdate();
        }
    }

    @Override
    public void activate(String kind, long batchId) throws Exception {
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE oms_reco_batch SET status = ? WHERE kind = ? AND status = ? AND id <> ?")) {
                ps.setString(1, BatchStatus.SUPERSEDED.name());
                ps.setString(2, kind);
                ps.setString(3, BatchStatus.ACTIVE.name());
                ps.setLong(4, batchId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("UPDATE oms_reco_batch SET status = ? WHERE id = ? AND status = ?")) {
                ps.setString(1, BatchStatus.ACTIVE.name());
                ps.setLong(2, batchId);
                ps.setString(3, BatchStatus.READY.name());
                if (ps.executeUpdate() != 1) {
                    throw new IllegalStateException("批次 " + batchId + " 不是 READY，不能启用");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO oms_reco_active (kind, batch_id, switched_at) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE batch_id = VALUES(batch_id), switched_at = VALUES(switched_at)")) {
                ps.setString(1, kind);
                ps.setLong(2, batchId);
                ps.setTimestamp(3, now());
                ps.executeUpdate();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
    }

    @Override
    public int prune(String kind, int keep) throws Exception {
        // 要删明细的批次：REJECTED / FAILED 的全部；成功的（ACTIVE/SUPERSEDED/READY）只留最新 keep 个；生效批次永远留
        List<Long> doomed = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, status FROM oms_reco_batch WHERE kind = ? ORDER BY id DESC")) {
            ps.setString(1, kind);
            try (ResultSet rs = ps.executeQuery()) {
                int kept = 0;
                while (rs.next()) {
                    long id = rs.getLong(1);
                    String st = rs.getString(2);
                    if (BatchStatus.ACTIVE.name().equals(st)) {
                        kept++;
                    } else if (BatchStatus.SUPERSEDED.name().equals(st) || BatchStatus.READY.name().equals(st)) {
                        if (kept < keep) {
                            kept++;
                        } else {
                            doomed.add(id);
                        }
                    } else if (BatchStatus.REJECTED.name().equals(st) || BatchStatus.FAILED.name().equals(st)) {
                        doomed.add(id);
                    }
                }
            }
        }
        int pruned = 0;
        for (long id : doomed) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM oms_spu_complement WHERE batch_id = ?")) {
                ps.setLong(1, id);
                if (ps.executeUpdate() > 0) {
                    pruned++;
                }
            }
        }
        return pruned;
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}

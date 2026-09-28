package com.mall.order.reco.batch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把「按订单 id 排好序」的订单行流拼成篮子。和 JDBC 解耦，好单测。
 *
 * <p>spu_id 为空/0 的行跳过并计数：它们来自 2026-09-23 修复之前的旧订单
 * （oms_order_item 从来没写过 spu_id），回填脚本补过，但批任务不能假设数据永远干净 ——
 * 跳过的行数写进批次统计，突然变多就说明上游又坏了。
 */
public final class BasketAccumulator {

    public record Baskets(List<long[]> baskets, Map<Long, Long> categoryOf, int orders, long skippedRows) {
    }

    private final List<long[]> baskets = new ArrayList<>();
    private final Map<Long, Long> categoryOf = new HashMap<>();
    private final List<Long> current = new ArrayList<>();
    private long currentOrder = Long.MIN_VALUE;
    private boolean started;
    private long lastOrder = Long.MIN_VALUE;
    private long skipped;

    /** 订单 id 必须非降序到达（SQL 里 ORDER BY o.id）；乱序说明 SQL 被改坏了，直接失败而不是算出错的篮子 */
    public void add(long orderId, Long spuId, Long categoryId) {
        if (started && orderId < lastOrder) {
            throw new IllegalStateException("订单行没有按 order id 排序：" + orderId + " 出现在 " + lastOrder + " 之后");
        }
        lastOrder = orderId;
        if (!started || orderId != currentOrder) {
            flush();
            currentOrder = orderId;
            started = true;
        }
        if (spuId == null || spuId == 0) {
            skipped++;
            return;
        }
        current.add(spuId);
        if (categoryId != null) {
            categoryOf.put(spuId, categoryId);
        }
    }

    public Baskets finish() {
        flush();
        return new Baskets(baskets, categoryOf, baskets.size(), skipped);
    }

    private void flush() {
        if (!current.isEmpty()) {
            baskets.add(current.stream().mapToLong(Long::longValue).toArray());
            current.clear();
        }
    }
}

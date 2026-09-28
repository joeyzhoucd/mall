package com.mall.order.reco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「搭配购买」：按同单共现 + LLR 给每个 SPU 算前 K 个搭配 SPU。纯计算，不碰数据库。
 *
 * <h3>规则（与离线评估 mall-deploy/eval/algorithms.js 相同，一致性测试逐条比对）</h3>
 * <ul>
 *   <li>一单里同一个 SPU 只算一次。</li>
 *   <li>候选只取和查询 SPU 至少共现过 1 次的；只留<b>正相关</b>的（c·N &gt; n<sub>A</sub>·n<sub>B</sub>，严格大于）。</li>
 *   <li>按 G² 降序，同分按 spuId 升序 —— 结果确定，重算不抖。</li>
 * </ul>
 * 在离线评估之外多一条（默认开，关掉才和评估逐条一致）：
 * <b>排除同类目</b>。同类目的替代品是「相似商品」那一块的事，两块推同一类东西就是重复。
 *
 * <h3>为什么不用 Map&lt;Long, Map&lt;Long, Integer&gt;&gt; 计数</h3>
 * 每一对都要装箱，十几万单就是几百万个小对象。这里把每一对编成一个 long
 * （小下标 &lt;&lt; 32 | 大下标）放进数组，排序后数连续相同值，再转成紧凑邻接表。
 * 内存 ≈ 8 字节 × Σ C(每单件数, 2)：12 万单、均 6 件约 14MB。
 * 到上千万单时这个数组放不下，要换成分块计数或 Spark —— 只换这一个类，表结构和在线服务不变。
 */
public final class ComplementCalculator {

    public record Candidate(long spuId, double score, int cooccur) {
    }

    public record Options(int topK, boolean excludeSameCategory) {
        public Options {
            if (topK <= 0) {
                throw new IllegalArgumentException("topK must be positive");
            }
        }
    }

    private ComplementCalculator() {
    }

    /**
     * @param baskets    每单的 SPU（可以有重复，内部去重）
     * @param categoryOf SPU → 类目；只在 excludeSameCategory 时用，查不到类目的不排除
     * @return 至少有一个候选的 SPU → 前 K 个搭配（按分数降序）
     */
    public static Map<Long, List<Candidate>> compute(List<long[]> baskets, Map<Long, Long> categoryOf, Options options) {
        // 1. SPU 编号 + 每个 SPU 出现在几单里
        Map<Long, Integer> indexOf = new HashMap<>();
        List<Long> ids = new ArrayList<>();
        int[][] dense = new int[baskets.size()][];
        for (int b = 0; b < baskets.size(); b++) {
            long[] distinct = Arrays.stream(baskets.get(b)).distinct().toArray();
            int[] row = new int[distinct.length];
            for (int k = 0; k < distinct.length; k++) {
                Integer i = indexOf.get(distinct[k]);
                if (i == null) {
                    i = ids.size();
                    indexOf.put(distinct[k], i);
                    ids.add(distinct[k]);
                }
                row[k] = i;
            }
            dense[b] = row;
        }
        int n = ids.size();
        long orders = baskets.size();
        int[] df = new int[n];
        long pairSlots = 0;
        for (int[] row : dense) {
            for (int i : row) {
                df[i]++;
            }
            pairSlots += (long) row.length * (row.length - 1) / 2;
        }
        if (pairSlots > Integer.MAX_VALUE - 8) {
            throw new IllegalStateException("共现对 " + pairSlots + " 超出单数组容量，需要分块计数");
        }

        // 2. 所有 (小下标, 大下标) 编码成 long，排序后数连续相同值 = 共现单数
        long[] codes = new long[(int) pairSlots];
        int p = 0;
        for (int[] row : dense) {
            for (int x = 0; x < row.length; x++) {
                for (int y = x + 1; y < row.length; y++) {
                    int lo = Math.min(row[x], row[y]), hi = Math.max(row[x], row[y]);
                    codes[p++] = ((long) lo << 32) | hi;
                }
            }
        }
        Arrays.sort(codes);

        // 3. 转成邻接表（CSR）：先数度，再填
        int[] degree = new int[n];
        forEachRun(codes, (lo, hi, c) -> {
            degree[lo]++;
            degree[hi]++;
        });
        int[] start = new int[n + 1];
        for (int i = 0; i < n; i++) {
            start[i + 1] = start[i] + degree[i];
        }
        int[] nbr = new int[start[n]];
        int[] cnt = new int[start[n]];
        int[] fill = Arrays.copyOf(start, n);
        forEachRun(codes, (lo, hi, c) -> {
            nbr[fill[lo]] = hi;
            cnt[fill[lo]++] = c;
            nbr[fill[hi]] = lo;
            cnt[fill[hi]++] = c;
        });

        // 4. 每个 SPU 打分、排序、截前 K
        Map<Long, List<Candidate>> out = new HashMap<>();
        for (int a = 0; a < n; a++) {
            long na = df[a];
            Long catA = options.excludeSameCategory() ? categoryOf.get(ids.get(a)) : null;
            List<Candidate> list = new ArrayList<>();
            for (int e = start[a]; e < start[a + 1]; e++) {
                int b = nbr[e];
                long c = cnt[e];
                long nb = df[b];
                if (c * orders <= na * nb) {
                    continue;   // 只留正相关
                }
                if (catA != null && catA.equals(categoryOf.get(ids.get(b)))) {
                    continue;   // 同类目交给「相似商品」
                }
                double g = Llr.g2(c, na - c, nb - c, orders - na - nb + c);
                list.add(new Candidate(ids.get(b), g, (int) c));
            }
            if (list.isEmpty()) {
                continue;
            }
            list.sort((x, y) -> x.score() != y.score()
                    ? Double.compare(y.score(), x.score())
                    : Long.compare(x.spuId(), y.spuId()));
            out.put(ids.get(a), List.copyOf(list.subList(0, Math.min(options.topK(), list.size()))));
        }
        return out;
    }

    private interface RunConsumer {
        void accept(int lo, int hi, int count);
    }

    private static void forEachRun(long[] sorted, RunConsumer consumer) {
        int i = 0;
        while (i < sorted.length) {
            int j = i;
            while (j < sorted.length && sorted[j] == sorted[i]) {
                j++;
            }
            consumer.accept((int) (sorted[i] >>> 32), (int) (sorted[i] & 0xFFFFFFFFL), j - i);
            i = j;
        }
    }
}

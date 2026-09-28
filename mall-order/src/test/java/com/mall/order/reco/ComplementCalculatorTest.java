package com.mall.order.reco;

import com.mall.order.reco.ComplementCalculator.Candidate;
import com.mall.order.reco.ComplementCalculator.Options;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ComplementCalculatorTest {

    // ------------------------------------------------------------------ LLR 本身

    @Test
    void llrEntropyFormEqualsDefinition() {
        Random r = new Random(1);
        double worst = 0;
        for (int t = 0; t < 1000; t++) {
            long[] k = {r.nextInt(500), r.nextInt(500), r.nextInt(500), r.nextInt(500)};
            if (k[0] + k[1] + k[2] + k[3] == 0) {
                continue;
            }
            double a = Llr.g2(k[0], k[1], k[2], k[3]);
            double b = Llr.g2Direct(k[0], k[1], k[2], k[3]);
            worst = Math.max(worst, Math.abs(a - b) / Math.max(1, Math.abs(b)));
        }
        assertThat(worst).isLessThan(1e-9);
    }

    /** 手算的已知值：[[10,0],[0,90]]，E = [[1,9],[9,81]]，G² = 2·(10·ln10 + 90·ln(90/81)) */
    @Test
    void llrMatchesHandComputedValue() {
        double expected = 2 * (10 * Math.log(10) + 90 * Math.log(90.0 / 81));
        assertThat(Llr.g2(10, 0, 0, 90)).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(expected).isCloseTo(65.0166, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(Llr.g2(5, 5, 5, 5)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-12));   // 完全独立
    }

    // ------------------------------------------------------------------ 与离线评估逐条一致

    /**
     * 标准答案由 mall-deploy/eval/export-golden.js 用离线评估的同一份 algorithms.js 生成
     * （批次 B 抽 1043 单，1336 个 SPU 各取前 20）。离线评估里「LLR 赢了」这个结论，
     * 只有在这里逐条相同时才适用于线上的这份 Java 实现。
     */
    @Test
    void matchesOfflineEvaluationExactly() throws IOException {
        List<long[]> baskets = new ArrayList<>();
        for (String line : readGz("/reco/golden/baskets.tsv.gz")) {
            baskets.add(Arrays.stream(line.split(",")).mapToLong(Long::parseLong).toArray());
        }
        Map<Long, List<Candidate>> got = ComplementCalculator.compute(baskets, Map.of(), new Options(20, false));

        int compared = 0, positionsCompared = 0, adjacentTies = 0;
        double worstScore = 0;
        for (String line : readGz("/reco/golden/expected.tsv.gz")) {
            String[] parts = line.split("\t", -1);
            long spu = Long.parseLong(parts[0]);
            List<long[]> want = new ArrayList<>();   // [spuId, scoreBits]
            List<Double> wantScores = new ArrayList<>();
            if (!parts[1].isEmpty()) {
                for (String item : parts[1].split(",")) {
                    String[] kv = item.split(":");
                    want.add(new long[]{Long.parseLong(kv[0])});
                    wantScores.add(Double.parseDouble(kv[1]));
                }
            }
            List<Candidate> mine = got.getOrDefault(spu, List.of());
            assertThat(mine.stream().map(Candidate::spuId).toList())
                    .as("SPU %d 的前 20 名", spu)
                    .containsExactlyElementsOf(want.stream().map(x -> x[0]).toList());
            for (int i = 0; i < mine.size(); i++) {
                worstScore = Math.max(worstScore, Math.abs(mine.get(i).score() - wantScores.get(i)) / Math.max(1, wantScores.get(i)));
                if (i > 0 && wantScores.get(i).equals(wantScores.get(i - 1))) {
                    adjacentTies++;
                }
                positionsCompared++;
            }
            compared++;
        }
        // 非空跑：资源没加载到、比对了 0 条也会「通过」—— 数量钉死
        assertThat(compared).isEqualTo(1336);
        // 17461 = 标准答案里名次总数（1000 单时很多 SPU 不足 20 个候选）。第一版写的是「> 20000」，
        // 那是估的、不是数的 —— 钉成实数，和上面的 1336 一样
        assertThat(positionsCompared).isEqualTo(17_461);
        // 标准答案里真有同分，「同分按 spuId 升序」这条规则才算被这个测试检验到
        assertThat(adjacentTies).as("标准答案里相邻同分的位置数").isGreaterThan(0);
        // 分数写出时保留 17 位有效数字，所以这里最多差 1 ulp 级别
        assertThat(worstScore).isLessThan(1e-15);
        System.out.printf("一致性：%d 个 SPU、%d 个名次逐条相同；其中相邻同分 %d 处；分数最大相对差 %.1e%n",
                compared, positionsCompared, adjacentTies, worstScore);
    }

    // ------------------------------------------------------------------ 规则

    @Test
    void keepsOnlyPositivelyAssociatedCandidates() {
        // A 出现在 50 单里，B 出现在 50 单里，只有 1 单一起 —— 期望 25，观测 1：负相关，不推
        List<long[]> baskets = new ArrayList<>();
        baskets.add(new long[]{1, 2});
        for (int i = 0; i < 49; i++) {
            baskets.add(new long[]{1, 100 + i});
            baskets.add(new long[]{2, 200 + i});
        }
        Map<Long, List<Candidate>> got = ComplementCalculator.compute(baskets, Map.of(), new Options(8, false));
        assertThat(got.getOrDefault(1L, List.of())).extracting(Candidate::spuId).doesNotContain(2L);
    }

    /**
     * 恰好独立（c·N = n_A·n_B）也不推：正相关是【严格】大于。
     * 这条是变异测试逼出来的 —— 把 &gt; 改成 &gt;= 时，上面那条（明显负相关）和一致性测试
     * （标准答案里恰好没有等号的情形）都不会红。
     */
    @Test
    void excludesExactlyIndependentCandidates() {
        // 4 单：A=1 出现 2 次，B=2 出现 2 次，一起 1 次 → 1×4 == 2×2
        List<long[]> baskets = List.of(new long[]{1, 2}, new long[]{1, 9}, new long[]{2, 8}, new long[]{7});
        Map<Long, List<Candidate>> got = ComplementCalculator.compute(baskets, Map.of(), new Options(8, false));
        assertThat(got.get(1L)).extracting(Candidate::spuId).containsExactly(9L);   // 9 是正相关（1×4 > 2×1）
    }

    @Test
    void excludesSameCategoryWhenAsked() {
        List<long[]> baskets = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            baskets.add(new long[]{1, 2, 3});      // 1 与 2 同类目，1 与 3 跨类目
            baskets.add(new long[]{900 + i});
        }
        Map<Long, Long> cat = Map.of(1L, 10L, 2L, 10L, 3L, 20L);
        assertThat(ComplementCalculator.compute(baskets, cat, new Options(8, true)).get(1L))
                .extracting(Candidate::spuId).containsExactly(3L);
        assertThat(ComplementCalculator.compute(baskets, cat, new Options(8, false)).get(1L))
                .extracting(Candidate::spuId).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void tiesAreBrokenBySpuIdAscending() {
        // 5、3、7 与 1 的共现结构完全对称 → 分数相同 → 按 spuId 升序
        List<long[]> baskets = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            baskets.add(new long[]{1, 5});
            baskets.add(new long[]{1, 3});
            baskets.add(new long[]{1, 7});
            baskets.add(new long[]{500 + i});
        }
        List<Candidate> got = ComplementCalculator.compute(baskets, Map.of(), new Options(8, false)).get(1L);
        assertThat(got).extracting(Candidate::score).containsOnly(got.get(0).score());
        assertThat(got).extracting(Candidate::spuId).containsExactly(3L, 5L, 7L);
    }

    @Test
    void duplicateSpuInOneBasketCountsOnce() {
        List<long[]> once = new ArrayList<>(), twice = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            once.add(new long[]{1, 2});
            twice.add(new long[]{1, 1, 2, 2});
            once.add(new long[]{800 + i});
            twice.add(new long[]{800 + i});
        }
        Candidate a = ComplementCalculator.compute(once, Map.of(), new Options(8, false)).get(1L).get(0);
        Candidate b = ComplementCalculator.compute(twice, Map.of(), new Options(8, false)).get(1L).get(0);
        assertThat(b.cooccur()).isEqualTo(a.cooccur()).isEqualTo(10);
        assertThat(b.score()).isEqualTo(a.score());
    }

    @Test
    void truncatesToTopK() {
        List<long[]> baskets = new ArrayList<>();
        for (int j = 2; j <= 30; j++) {
            for (int k = 0; k < j; k++) {
                baskets.add(new long[]{1, j});
            }
        }
        for (int i = 0; i < 400; i++) {
            baskets.add(new long[]{1000 + i});
        }
        assertThat(ComplementCalculator.compute(baskets, Map.of(), new Options(5, false)).get(1L)).hasSize(5);
    }

    private static List<String> readGz(String resource) throws IOException {
        InputStream in = ComplementCalculatorTest.class.getResourceAsStream(resource);
        assertThat(in).as("测试资源 %s（用 mall-deploy/eval/export-golden.js 生成）", resource).isNotNull();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (!l.isEmpty()) {
                    lines.add(l);
                }
            }
        }
        return lines;
    }
}

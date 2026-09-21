package com.mall.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 关键词检索和向量检索的<b>融合方式</b>。
 *
 * <h3>默认是 score-sum，而且这是量出来的，不是图省事</h3>
 * 2026-09-21 在真实的 14941 条商品上做过对照（5 组查询，看 top8 里
 * 「品类+品牌都对」的条数）：
 *
 * <pre>
 *   窗口     score-sum   rrf
 *    50        38/40    30/40
 *   200        38/40    34/40
 *   500        38/40    34/40
 * </pre>
 *
 * RRF 随窗口变大而改善，但始终追不上，还要付出双倍的数据传输。
 *
 * <h3>为什么 RRF 在这里反而更差</h3>
 * 两者<b>用到的信息量不同</b>：
 * <ul>
 *   <li>score-sum：ES 对<b>并集里每个文档</b>都算 BM25 分 + kNN 分，
 *       哪怕它没进某一路的 top-k</li>
 *   <li>rrf：只看各自 <b>top-window 内的排名</b>，掉出窗口 = 那一路零贡献</li>
 * </ul>
 * 「迪卡侬骑行运动」这种两路都该认可的商品，在 score-sum 里两个分数都高、
 * 稳居第一；在 RRF 里可能只挤进了一路的窗口，于是和只有单路认可的
 * 「迪卡侬健身器材」打成平手。实测 RRF 的 top8 几乎全是单路命中。
 *
 * <h3>那为什么还留着 rrf 这个选项</h3>
 * 因为上面的结论<b>依赖当前的数据特征</b>：这批商品标题是模板生成的
 * （「迪卡侬新款骑行运动 S 68 #9684 白色 豪华版」），品牌和品类都是精确词，
 * BM25 在这种数据上几乎无敌。真实电商的标题有口语化表达、错别字、同义词，
 * BM25 不会这么强，RRF 的「平等对待两路」就可能反过来占优。
 * <p>
 * 所以准确的说法是<b>「在当前数据上 RRF 没有收益」</b>，而不是「RRF 不行」。
 * 换了数据应该重新量一次 —— 留这个开关就是为了那时不用改代码。
 * 评估脚本的做法见 PLAN.md 里程碑 G。
 */
@ConfigurationProperties(prefix = "mall.search.fusion")
public record SearchFusionProperties(
        String mode,
        Integer rrfWindow,
        Integer rrfK
) {

    /** ES 原生：query + knn 放一个请求，两路分数相加。默认。 */
    public static final String MODE_SCORE_SUM = "score-sum";
    /** 应用层 Reciprocal Rank Fusion：只看排名，不看分数。 */
    public static final String MODE_RRF = "rrf";

    public SearchFusionProperties {
        if (mode == null || mode.isBlank()) {
            mode = MODE_SCORE_SUM;
        }
        mode = mode.trim().toLowerCase();
        if (!MODE_SCORE_SUM.equals(mode) && !MODE_RRF.equals(mode)) {
            throw new IllegalArgumentException(
                    "mall.search.fusion.mode 只能是 " + MODE_SCORE_SUM + " 或 " + MODE_RRF + "，实际是：" + mode);
        }
        // 每一路各取多少条参与融合。实测 50 太小（两路交集不足，RRF 退化成
        // 「两个列表交替取」），200 是效果和传输量的折中。
        if (rrfWindow == null || rrfWindow < 1) {
            rrfWindow = 200;
        }
        // RRF 公式 1/(k+rank) 里的 k。60 是这个算法原始论文里的取值，
        // 也是业界惯例。它的作用是压平头部差距：k 越大，第 1 名和第 10 名的
        // 分差越小，两路的「都还行」就越容易胜过一路的「特别好」。
        if (rrfK == null || rrfK < 1) {
            rrfK = 60;
        }
    }

    public boolean isRrf() {
        return MODE_RRF.equals(mode);
    }
}

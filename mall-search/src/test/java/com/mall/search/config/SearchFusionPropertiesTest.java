package com.mall.search.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 融合模式配置的守护测试。
 *
 * <p>重点是<b>「配错了必须炸」</b>那一条。默认值兜底是好事，但如果把
 * {@code mode: rff}（拼错）也兜成 score-sum，运维会以为自己开启了 RRF，
 * 而系统跑的是另一套 —— 这种「配了没生效且不报错」的失效方式，
 * 本仓库已经吃过好几次亏（见 mall-common 的静默失败清单）。
 */
class SearchFusionPropertiesTest {

    @Test
    @DisplayName("默认是 score-sum —— 实测在当前数据上它优于 RRF")
    void defaultsToScoreSum() {
        SearchFusionProperties p = new SearchFusionProperties(null, null, null);

        assertThat(p.mode()).isEqualTo(SearchFusionProperties.MODE_SCORE_SUM);
        assertThat(p.isRrf()).isFalse();
    }

    @Test
    @DisplayName("显式配 rrf 时识别为 RRF 模式，大小写不敏感")
    void recognisesRrf() {
        assertThat(new SearchFusionProperties("rrf", null, null).isRrf()).isTrue();
        assertThat(new SearchFusionProperties("RRF", null, null).isRrf()).isTrue();
        assertThat(new SearchFusionProperties("  rrf  ", null, null).isRrf()).isTrue();
    }

    @Test
    @DisplayName("mode 写错必须启动失败，不能静默当成默认值")
    void rejectsUnknownMode() {
        // 「配了没生效且不报错」是最难查的一类问题：运维以为开了 RRF，
        // 监控上一切正常，只有检索质量和预期不符，而那很难被察觉。
        assertThatThrownBy(() -> new SearchFusionProperties("rff", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rff");

        assertThatThrownBy(() -> new SearchFusionProperties("hybrid", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("空字符串按未配置处理，不算配错")
    void blankIsTreatedAsUnset() {
        // 和上面一条的区别：空值是「没配」，拼错是「配错了」。
        // 前者用默认值是对的，后者必须报错。
        assertThat(new SearchFusionProperties("", null, null).mode())
                .isEqualTo(SearchFusionProperties.MODE_SCORE_SUM);
        assertThat(new SearchFusionProperties("   ", null, null).mode())
                .isEqualTo(SearchFusionProperties.MODE_SCORE_SUM);
    }

    @Test
    @DisplayName("窗口和 k 的默认值：200 / 60")
    void fusionDefaults() {
        SearchFusionProperties p = new SearchFusionProperties("rrf", null, null);

        // 50 实测太小（两路交集不足，RRF 退化成「两个列表交替取」），200 是折中
        assertThat(p.rrfWindow()).isEqualTo(200);
        // 60 是 RRF 原始论文的取值，也是业界惯例
        assertThat(p.rrfK()).isEqualTo(60);
    }

    @Test
    @DisplayName("非法的窗口和 k 回退到默认值，而不是让检索用 0 或负数")
    void invalidNumbersFallBack() {
        SearchFusionProperties zero = new SearchFusionProperties("rrf", 0, 0);
        SearchFusionProperties negative = new SearchFusionProperties("rrf", -5, -1);

        // window=0 会让两路都取空，k=0 会让 1/(0+1)=1 把第一名的权重放到最大——
        // 都不是「保守降级」，而是行为完全变样，所以不接受这些值。
        assertThat(zero.rrfWindow()).isEqualTo(200);
        assertThat(zero.rrfK()).isEqualTo(60);
        assertThat(negative.rrfWindow()).isEqualTo(200);
        assertThat(negative.rrfK()).isEqualTo(60);
    }
}

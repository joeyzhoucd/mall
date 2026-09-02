package com.mall.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BusinessMetrics} 的行为约束。
 *
 * <h3>这个测试真正要防的东西</h3>
 * 业务埋点最贵的事故不是「指标记错了」，而是<b>把无界的字符串当成标签传进去</b>：
 * 上线时一切正常，序列数每天涨一点，几天后 Prometheus OOM。
 * 那时候已经抓取的样本删不掉，只能等过期。
 * <p>
 * 所以下面的重点不是「success 会不会 +1」这种显然的事，而是：
 * <ul>
 *   <li>护栏到顶之后<b>序列数真的不再增长</b>（而不只是打了条 warn）；</li>
 *   <li>护栏在到顶<b>之前不会误伤</b>——负控制，否则一个「无脑把一切归并到
 *       _overflow」的实现也能让上面那条通过，测试就白写了；</li>
 *   <li>各业务流的护栏互相独立，一条流刷爆不会连带屏蔽别的流。</li>
 * </ul>
 */
class BusinessMetricsTest {

    private static final String FLOW = "test.flow";

    private SimpleMeterRegistry registry() {
        return new SimpleMeterRegistry();
    }

    private Counter find(SimpleMeterRegistry reg, String flow, String result, String reason) {
        return reg.find(BusinessMetrics.METRIC_NAME)
                .tag("flow", flow)
                .tag("result", result)
                .tag("reason", reason)
                .counter();
    }

    private List<Meter> metersOfFlow(SimpleMeterRegistry reg, String flow) {
        List<Meter> out = new ArrayList<>();
        for (Meter m : reg.getMeters()) {
            if (BusinessMetrics.METRIC_NAME.equals(m.getId().getName())
                    && flow.equals(m.getId().getTag("flow"))) {
                out.add(m);
            }
        }
        return out;
    }

    @Test
    @DisplayName("success 记成 result=success、reason=none")
    void successUsesNoneReason() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        metrics.success(FLOW);
        metrics.success(FLOW);

        Counter c = find(reg, FLOW, "success", BusinessMetrics.NO_REASON);
        assertNotNull(c, "success 应该产生 reason=" + BusinessMetrics.NO_REASON + " 的计数器");
        assertEquals(2.0, c.count());
        // 空标签值在 Prometheus 里和「标签缺失」很难区分，所以刻意不允许出现空 reason
        assertNull(find(reg, FLOW, "success", ""), "reason 不应该出现空字符串");
    }

    @Test
    @DisplayName("failure 带上 reason；空白 reason 退回 none")
    void failureCarriesReason() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        metrics.failure(FLOW, "stock_lock_failed");
        metrics.failure(FLOW, null);
        metrics.failure(FLOW, "   ");

        assertEquals(1.0, find(reg, FLOW, "failure", "stock_lock_failed").count());
        assertEquals(2.0, find(reg, FLOW, "failure", BusinessMetrics.NO_REASON).count(),
                "null 和纯空白都应该归到 none，而不是各建一条序列");
    }

    @Test
    @DisplayName("同一组合重复记录只加计数，不新增序列")
    void repeatedComboDoesNotGrowSeries() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        for (int i = 0; i < 100; i++) {
            metrics.failure(FLOW, "price_changed");
        }

        assertEquals(1, metersOfFlow(reg, FLOW).size(), "100 次同一组合只该有 1 条序列");
        assertEquals(1, metrics.seenCombinationCount(FLOW));
        assertEquals(100.0, find(reg, FLOW, "failure", "price_changed").count());
    }

    @Test
    @DisplayName("护栏到顶后序列数不再增长，多出来的归到 _overflow")
    void cardinalityGuardCapsSeriesCount() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        int over = 40;
        int cap = BusinessMetrics.MAX_COMBINATIONS_PER_FLOW;
        for (int i = 0; i < over; i++) {
            // 模拟「有人把异常消息当 reason 传进来」——每次都是新值
            metrics.failure(FLOW, "boom_" + i);
        }

        assertEquals(cap, metrics.seenCombinationCount(FLOW),
                "已见组合数应该正好停在上限");
        assertEquals(cap + 1, metersOfFlow(reg, FLOW).size(),
                "序列数应该是 上限 + 1 条 _overflow，而不是 " + over + " 条");

        Counter overflow = find(reg, FLOW, "failure", BusinessMetrics.OVERFLOW_REASON);
        assertNotNull(overflow, "超限的记录应该落到 " + BusinessMetrics.OVERFLOW_REASON);
        assertEquals((double) (over - cap), overflow.count(),
                "超限的次数不能丢，只是失去了细节");

        // 再刷 1000 次新 reason，序列数必须一动不动 —— 这才是护栏的意义
        for (int i = 0; i < 1000; i++) {
            metrics.failure(FLOW, "flood_" + i);
        }
        assertEquals(cap + 1, metersOfFlow(reg, FLOW).size(),
                "护栏到顶之后再怎么刷都不该长出新序列");
    }

    @Test
    @DisplayName("负控制：正好到上限时不产生 _overflow（证明护栏不是无脑归并一切）")
    void guardDoesNotFirePrematurely() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        int cap = BusinessMetrics.MAX_COMBINATIONS_PER_FLOW;
        for (int i = 0; i < cap; i++) {
            metrics.failure(FLOW, "reason_" + i);
        }

        assertNull(find(reg, FLOW, "failure", BusinessMetrics.OVERFLOW_REASON),
                "还没超限就出现 _overflow，说明护栏提前触发了");
        assertEquals(cap, metersOfFlow(reg, FLOW).size());
        // 每一个都还保留着自己的 reason，细节没丢
        assertEquals(1.0, find(reg, FLOW, "failure", "reason_0").count());
        assertEquals(1.0, find(reg, FLOW, "failure", "reason_" + (cap - 1)).count());
    }

    @Test
    @DisplayName("各业务流的护栏互相独立")
    void guardIsPerFlow() {
        SimpleMeterRegistry reg = registry();
        BusinessMetrics metrics = new BusinessMetrics(reg);

        // 把一条流刷爆
        for (int i = 0; i < BusinessMetrics.MAX_COMBINATIONS_PER_FLOW + 10; i++) {
            metrics.failure("noisy.flow", "boom_" + i);
        }
        // 另一条流应该完全不受影响
        metrics.failure("quiet.flow", "insufficient");
        metrics.success("quiet.flow");

        assertEquals(1.0, find(reg, "quiet.flow", "failure", "insufficient").count(),
                "一条流刷爆不该连带屏蔽别的流");
        assertEquals(2, metersOfFlow(reg, "quiet.flow").size());
        assertNull(find(reg, "quiet.flow", "failure", BusinessMetrics.OVERFLOW_REASON));
    }

    @Test
    @DisplayName("BusinessFlow 的常量必须是 Prometheus 安全的有界字面量")
    void businessFlowConstantsAreLabelSafe() throws Exception {
        // 这条守的是「有人往 BusinessFlow 里加一个带空格/大写/中文/占位符的常量」。
        // 那种值一旦进了标签，面板和告警规则匹配不到，而编译完全通过 ——
        // 也就是这个项目反复踩到的静默失败形状。
        Pattern safe = Pattern.compile("^[a-z0-9_.]+$");
        int checked = 0;
        for (Field f : BusinessFlow.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) {
                continue;
            }
            f.setAccessible(true);
            String value = (String) f.get(null);
            assertTrue(safe.matcher(value).matches(),
                    "BusinessFlow." + f.getName() + " = \"" + value
                            + "\" 不是标签安全的（只允许小写字母、数字、下划线、点）");
            checked++;
        }
        assertTrue(checked >= 10, "只检查到 " + checked + " 个常量，反射大概是没生效");
    }

    @Test
    @DisplayName("BusinessFlow 里不该有没人用的常量")
    void noUnusedBusinessFlowConstants() throws Exception {
        // 死常量是下一个「面板匹配不到数据」的种子：它看起来像一个可用的 reason，
        // 有人照着写进告警规则，而代码里从来不会产生这个值。
        // 这里只能检查源码里是否被引用，所以要求项目根目录可见；找不到就跳过而不是假过。
        java.nio.file.Path root = java.nio.file.Paths.get("").toAbsolutePath();
        java.nio.file.Path srcRoot = root.getParent();  // mall-common -> mall-backend
        if (srcRoot == null || !java.nio.file.Files.isDirectory(srcRoot)) {
            return;
        }

        List<String> names = new ArrayList<>();
        for (Field f : BusinessFlow.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                names.add(f.getName());
            }
        }

        // 整棵树只遍历一次，把源码拼成一份文本再查 —— 按常量逐个 walk 会慢一个量级
        StringBuilder allSources = new StringBuilder();
        int scanned = 0;
        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.walk(srcRoot)) {
            for (java.nio.file.Path p : files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("BusinessFlow.java"))
                    .filter(p -> !p.getFileName().toString().equals("BusinessMetricsTest.java"))
                    .toList()) {
                try {
                    allSources.append(java.nio.file.Files.readString(p)).append('\n');
                    scanned++;
                } catch (Exception ignored) {
                    // 读不了的文件跳过，不影响结论方向（只会漏报，不会误报）
                }
            }
        }
        // 没扫到源码就说明路径假设错了，那这个测试是「假通过」——直接失败比静默放过好
        assertTrue(scanned > 100, "只扫到 " + scanned + " 个 java 文件，源码根目录假设有问题: " + srcRoot);

        String sources = allSources.toString();
        List<String> unused = new ArrayList<>();
        for (String name : names) {
            if (!sources.contains("BusinessFlow." + name)) {
                unused.add(name);
            }
        }

        assertTrue(unused.isEmpty(),
                "BusinessFlow 里这些常量没有任何调用方，要么去埋点要么删掉: " + unused);
    }
}

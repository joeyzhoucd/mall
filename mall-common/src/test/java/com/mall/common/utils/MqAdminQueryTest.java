package com.mall.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住消息治理四个页面的筛选和排序。
 *
 * <h3>为什么一份测试守四个服务</h3>
 * 订单/库存的 Outbox 表和消费幂等表结构逐列相同，四个 Service 都调
 * {@link MqAdminQuery} 的这两个方法。所以拼装逻辑只有一份，测试也只需要一份 ——
 * 这正是把它抽到 mall-common 的理由。
 *
 * <h3>怎么测</h3>
 * 直接断言拼出来的 SQL 片段。条件拼装是纯逻辑、不需要数据库，
 * 而 SQL 片段恰好能同时验证「筛选加了没有」和「排序落在哪一列」。
 */
class MqAdminQueryTest {

    private static Map<String, Object> params(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // -----------------------------------------------------------------------
    // 排序
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Outbox：不带条件也必须有排序，且以唯一列 id 收尾")
    void outboxAlwaysOrdersByUniqueColumn() {
        String sql = MqAdminQuery.outbox(params()).getSqlSegment();
        assertTrue(sql.contains("ORDER BY"), "缺少 ORDER BY，分页会变成未定义行为：" + sql);
        String orderBy = sql.substring(sql.indexOf("ORDER BY"));
        assertTrue(orderBy.contains("id"), "排序没有落在唯一列 id 上：" + orderBy);
        assertFalse(sql.contains("LIKE"), "没传条件却拼出了模糊匹配：" + sql);
    }

    /**
     * 消费幂等表按 update_time 排，但<b>必须再用 id 收尾</b>。
     *
     * <p>update_time 不唯一 —— 同一批重投会撞在同一秒。
     * 只按它排的话并列行之间顺序未定义，翻页照样重复或漏行。
     * 这正是「看起来加了排序、实际没解决问题」的情况，所以单独守一次。
     */
    @Test
    @DisplayName("消费幂等：update_time 在前、id 收尾，两者缺一不可")
    void consumeOrdersByUpdateTimeThenId() {
        String sql = MqAdminQuery.consume(params()).getSqlSegment();
        int at = sql.indexOf("ORDER BY");
        assertTrue(at >= 0, "没有 ORDER BY：" + sql);
        String orderBy = sql.substring(at);

        assertTrue(orderBy.contains("update_time"), "没有按 update_time 排序：" + orderBy);
        assertTrue(orderBy.contains("id"), "排序没有以唯一列 id 收尾：" + orderBy);
        assertTrue(orderBy.indexOf("update_time") < orderBy.lastIndexOf("id"),
                "id 应当是次序键，排在 update_time 之后：" + orderBy);
    }

    // -----------------------------------------------------------------------
    // 模糊搜索的括号 —— 这一条是本文件里最重要的
    // -----------------------------------------------------------------------

    /**
     * {@code key} 拼出来的 OR <b>必须被括起来</b>。
     *
     * <p>没有括号的话，SQL 会变成
     * <pre>status = 4 AND message_key LIKE x OR business_key LIKE x</pre>
     * 而 AND 的优先级高于 OR，等价于
     * <pre>(status = 4 AND message_key LIKE x) OR (business_key LIKE x)</pre>
     * —— 右半边<b>完全不受 status 约束</b>。
     * 表现是「筛『死信』，却查出一堆已发送的」，
     * 而这看起来像后端筛选没生效，很难指向括号。
     */
    @Test
    @DisplayName("Outbox：key 的 OR 必须括起来，否则会吞掉 status 条件")
    void outboxKeyOrIsParenthesized() {
        String sql = MqAdminQuery.outbox(params("status", "4", "key", "order.close")).getSqlSegment();

        assertTrue(sql.contains("status ="), "status 条件丢了：" + sql);
        int at = sql.indexOf("message_key LIKE");
        assertTrue(at > 0, "没有拼出模糊匹配：" + sql);

        String before = sql.substring(0, at).trim();
        assertTrue(before.endsWith("("),
                "OR 组没有被括起来，它会把 status 条件吞掉。LIKE 之前的片段是：«" + before + "»");
    }

    /**
     * 反向对照：确认上面那条断言<b>真的能失败</b>。
     *
     * <p>这里手工拼一个「忘了包 and(...)」的 wrapper —— 也就是改坏之后的样子，
     * 它必须让同一条断言不成立。否则上面那条测试就是永远通过的，
     * 给的是虚假的安全感。
     */
    @Test
    @DisplayName("反向对照：不包 and(...) 时，括号断言确实不成立")
    void unparenthesizedOrFailsTheSameAssertion() {
        QueryWrapper<Object> broken = new QueryWrapper<>();
        broken.eq("status", 4);
        broken.like("message_key", "order.close").or().like("business_key", "order.close");

        String sql = broken.getSqlSegment();
        int at = sql.indexOf("message_key LIKE");
        assertTrue(at > 0, "反向对照本身没拼出 LIKE：" + sql);

        String before = sql.substring(0, at).trim();
        assertFalse(before.endsWith("("),
                "反向对照不成立：没包 and(...) 却也括起来了，说明这条断言测不出问题。片段：«" + before + "»");
    }

    // -----------------------------------------------------------------------
    // 各个筛选条件
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Outbox：businessType / businessKey / messageKey 是精确匹配")
    void outboxExactFilters() {
        String sql = MqAdminQuery.outbox(params(
                "businessType", "ORDER_CLOSE",
                "businessKey", "20260906001",
                "messageKey", "order.close.20260906001")).getSqlSegment();

        assertTrue(sql.contains("business_type ="), "businessType 没生效：" + sql);
        assertTrue(sql.contains("business_key ="), "businessKey 没生效：" + sql);
        assertTrue(sql.contains("message_key ="), "messageKey 没生效：" + sql);
        assertFalse(sql.contains("LIKE"), "这三个不该用模糊匹配：" + sql);
    }

    @Test
    @DisplayName("消费幂等：consumerGroup 精确匹配 —— 用来定位某一个监听器")
    void consumeFiltersByConsumerGroup() {
        String sql = MqAdminQuery.consume(params("consumerGroup", "stock-deduct-listener")).getSqlSegment();
        assertTrue(sql.contains("consumer_group ="), "consumerGroup 没生效：" + sql);
    }

    @Test
    @DisplayName("空白参数视同没传，不能拼出把全表都排除掉的条件")
    void blankParamsAreIgnored() {
        for (String blank : new String[] { "", "   ", "\t" }) {
            String sql = MqAdminQuery.outbox(params(
                    "key", blank, "businessType", blank, "status", blank)).getSqlSegment();
            assertFalse(sql.contains("LIKE"),
                    "空白不该产生 LIKE（输入是 «" + blank.replace("\t", "\\t") + "»）：" + sql);
            assertFalse(sql.contains("business_type ="), "空白不该产生等值条件：" + sql);
            assertFalse(sql.contains("status ="), "空白不该产生 status 条件：" + sql);
        }
    }

    @Test
    @DisplayName("status 解析不了时当作没传，而不是抛异常变成 500")
    void unparseableStatusIsIgnored() {
        // 状态下拉框的「全部」被序列化成 "undefined" 是很常见的。
        for (String bad : new String[] { "abc", "undefined", "null", "1.5" }) {
            String sql = MqAdminQuery.outbox(params("status", bad)).getSqlSegment();
            assertFalse(sql.contains("status ="), "非法 status «" + bad + "» 不该拼进条件：" + sql);
            assertTrue(sql.contains("ORDER BY"), "即便如此排序也要在：" + sql);
        }
    }

    @Test
    @DisplayName("status=0 必须生效 —— 0 是合法状态（PENDING），不能被当成「没传」")
    void statusZeroIsAValidFilter() {
        String sql = MqAdminQuery.outbox(params("status", "0")).getSqlSegment();
        assertTrue(sql.contains("status ="),
                "status=0 被吞掉了。0 是 PENDING，是最需要筛的状态之一：" + sql);
    }

    // -----------------------------------------------------------------------
    // 状态统计
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("状态统计：COUNT(*) 的类型不确定，按 Number 取而不是强转")
    void statusCountsAcceptsAnyNumericType() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("status", 0, "cnt", 3L));                       // Long
        rows.add(row("status", 4, "cnt", java.math.BigInteger.valueOf(7)));  // BigInteger
        rows.add(row("status", 2, "cnt", java.math.BigDecimal.valueOf(11))); // BigDecimal

        Map<Integer, Long> counts = MqAdminQuery.toStatusCounts(rows);

        assertEquals(3L, counts.get(0), "Long 没解析出来");
        assertEquals(7L, counts.get(4), "BigInteger 没解析出来");
        assertEquals(11L, counts.get(2), "BigDecimal 没解析出来");
    }

    @Test
    @DisplayName("状态统计：列名大小写两种都认，脏行跳过而不是整体失败")
    void statusCountsToleratesShapeVariations() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("STATUS", 1, "CNT", 5L));   // 大写列名
        rows.add(row("status", null, "cnt", 9L)); // status 为 null，跳过
        rows.add(null);                            // 整行 null，跳过

        Map<Integer, Long> counts = MqAdminQuery.toStatusCounts(rows);

        assertEquals(5L, counts.get(1), "大写列名没认出来");
        assertEquals(1, counts.size(), "脏行应当被跳过而不是混进结果：" + counts);
    }

    @Test
    @DisplayName("状态统计：null 输入返回空 map，不抛异常")
    void statusCountsHandlesNull() {
        assertTrue(MqAdminQuery.toStatusCounts(null).isEmpty());
    }

    @Test
    @DisplayName("状态统计的查询必须真的带 GROUP BY，否则统计出来的是一个数")
    void statusCountQueryGroupsByStatus() {
        String sql = MqAdminQuery.statusCountQuery().getSqlSegment();
        assertTrue(sql.contains("GROUP BY"), "缺少 GROUP BY：" + sql);
        assertTrue(sql.contains("status"), "没按 status 分组：" + sql);
    }

    private static Map<String, Object> row(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}

package com.mall.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.member.entity.MemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住后台会员查询的筛选和排序。
 *
 * <h3>调的是生产代码，不是复刻的一份</h3>
 * 上周做订单查询时在这件事上栽过：测试里自己重写了一遍条件拼装
 * （理由是 queryPage 要连数据库），做正向对照时才发现 ——
 * 把生产代码的 {@code orderByDesc} 整行删掉，8 条测试全部照常通过。
 * 一个永远通过的测试比没有测试更糟。
 * <p>
 * 所以这里和订单那边一样，把拼装抽成了包内可见的 buildQueryWrapper，
 * 测试直接调它。构造参数传 null 是安全的 ——
 * buildQueryWrapper 只读 params，不碰 memberLevelService。
 */
class MemberQueryPageTest {

    private static QueryWrapper<MemberEntity> build(Map<String, Object> params) {
        return new MemberServiceImpl(null).buildQueryWrapper(params);
    }

    private static Map<String, Object> params(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("不带任何条件时也必须有确定的排序")
    void noFilterStillOrders() {
        String sql = build(params()).getSqlSegment();
        assertTrue(sql.contains("ORDER BY"), "缺少 ORDER BY —— 分页会变成未定义行为：" + sql);
        assertFalse(sql.contains("LIKE"), "没传条件却拼出了模糊匹配：" + sql);
    }

    /**
     * 排序必须以唯一列 id 收尾。
     *
     * <p>当前 103 个会员里有 100 个是同一批种子数据导入的，
     * create_time 几乎全部落在同一秒。只按时间排的话，
     * 这 100 行之间的顺序完全未定义，翻页时会重复或漏掉 ——
     * 而且行数越多越明显。
     */
    @Test
    @DisplayName("排序要以唯一列 id 收尾，否则批量导入的会员翻页会重复")
    void orderingIsTotal() {
        String sql = build(params()).getSqlSegment();
        int at = sql.indexOf("ORDER BY");
        assertTrue(at >= 0, "没有 ORDER BY：" + sql);
        String orderBy = sql.substring(at);

        assertTrue(orderBy.contains("create_time"), "没有按 create_time 排序：" + orderBy);
        assertTrue(orderBy.contains("id"), "排序没有以唯一列 id 收尾：" + orderBy);
        assertTrue(orderBy.indexOf("create_time") < orderBy.lastIndexOf("id"),
                "id 应当是次序键，排在 create_time 之后：" + orderBy);
    }

    @Test
    @DisplayName("key 同时匹配用户名、昵称和手机号")
    void keySearchesThreeColumns() {
        String sql = build(params("key", "joey")).getSqlSegment();
        assertTrue(sql.contains("username LIKE"), "没有匹配用户名：" + sql);
        assertTrue(sql.contains("nickname LIKE"), "没有匹配昵称：" + sql);
        assertTrue(sql.contains("mobile LIKE"), "没有匹配手机号：" + sql);
    }

    /**
     * key 的 OR 必须括起来，否则会把 levelId / status 条件吞掉。
     *
     * <p>AND 优先级高于 OR，没括号的话
     * {@code level_id = 3 AND username LIKE x OR nickname LIKE x}
     * 等价于 {@code (level_id = 3 AND username LIKE x) OR (nickname LIKE x)}，
     * 右半边不受等级约束 —— 表现是「筛了等级，结果还是全部」。
     */
    @Test
    @DisplayName("key 的 OR 必须括起来，否则等级筛选会失效")
    void keyOrIsParenthesized() {
        String sql = build(params("key", "joey", "levelId", "3")).getSqlSegment();
        assertTrue(sql.contains("level_id ="), "levelId 条件丢了：" + sql);

        int at = sql.indexOf("username LIKE");
        assertTrue(at > 0, "没有拼出模糊匹配：" + sql);
        String before = sql.substring(0, at).trim();
        assertTrue(before.endsWith("("),
                "OR 组没有被括起来，它会吞掉 level_id 条件。LIKE 之前的片段是：«" + before + "»");
    }

    @Test
    @DisplayName("空白参数视同没传")
    void blankParamsAreIgnored() {
        for (String blank : new String[] { "", "   ", "\t" }) {
            String sql = build(params("key", blank, "levelId", blank, "status", blank)).getSqlSegment();
            assertFalse(sql.contains("LIKE"),
                    "空白不该产生 LIKE（输入是 «" + blank.replace("\t", "\\t") + "»）：" + sql);
            assertFalse(sql.contains("level_id ="), "空白不该产生等级条件：" + sql);
        }
    }

    @Test
    @DisplayName("非法的 levelId / status 当作没传，而不是抛异常变成 500")
    void unparseableFiltersAreIgnored() {
        String sql = build(params("levelId", "abc", "status", "undefined")).getSqlSegment();
        assertFalse(sql.contains("level_id ="), "非法 levelId 不该拼进条件：" + sql);
        assertFalse(sql.contains("status ="), "非法 status 不该拼进条件：" + sql);
        assertTrue(sql.contains("ORDER BY"), "即便如此排序也要在：" + sql);
    }

    @Test
    @DisplayName("status=0 必须生效 —— 0 是合法状态，不能被当成「没传」")
    void statusZeroIsAValidFilter() {
        String sql = build(params("status", "0")).getSqlSegment();
        assertTrue(sql.contains("status ="), "status=0 被吞掉了：" + sql);
    }

    @Test
    @DisplayName("时间范围两端可以单独给")
    void dateRangeEndsAreIndependent() {
        String onlyFrom = build(params("createTimeFrom", "2026-09-01 00:00:00")).getSqlSegment();
        assertTrue(onlyFrom.contains("create_time >="), "只给开始时间应当生成 >= ：" + onlyFrom);
        assertFalse(onlyFrom.contains("create_time <="), "没给结束时间却生成了 <= ：" + onlyFrom);

        String onlyTo = build(params("createTimeTo", "2026-09-30 23:59:59")).getSqlSegment();
        assertTrue(onlyTo.contains("create_time <="), "只给结束时间应当生成 <= ：" + onlyTo);
    }

    /**
     * 反向对照：确认上面那些断言真的能失败。
     *
     * <p>手工构造一个「退化成生成器原样」的空 wrapper
     * （MemberServiceImpl 改之前就是 {@code new QueryWrapper<>()}），
     * 它必须让排序和筛选的断言全部不成立。
     */
    @Test
    @DisplayName("反向对照：退化成空 wrapper 时，上面的断言确实会失败")
    void negativeControl() {
        QueryWrapper<MemberEntity> degraded = new QueryWrapper<>();
        String sql = degraded.getSqlSegment();

        assertFalse(sql.contains("ORDER BY"), "反向对照本身不成立：空 wrapper 不该有 ORDER BY");
        assertFalse(sql.contains("LIKE"), "反向对照本身不成立：空 wrapper 不该有 LIKE");
        assertEquals("", sql.trim(), "空 wrapper 的 SQL 片段应当是空的，实际是：" + sql);
    }
}

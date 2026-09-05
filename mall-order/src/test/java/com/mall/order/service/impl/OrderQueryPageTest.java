package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.order.entity.OrderEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住后台订单查询的筛选和排序。
 *
 * <h3>为什么值得写</h3>
 * 原实现是生成器的裸模板：空 QueryWrapper、没有筛选、<b>没有 ORDER BY</b>。
 * 两条都属于「改坏了也不报错」：
 * <ul>
 *   <li>筛选没生效 → 搜索框能输入、点查询没反应、结果还是全部。
 *       这个项目里已经在品牌、属性分组上各撞过一次。</li>
 *   <li>没有确定排序 → 分页的每一页是独立的无序查询，行会重复或漏掉。
 *       数据少时完全看不出来 —— spuinfo 那次是 10004 行时
 *       第 1、2 页实测重复 8 行才暴露的。订单会长得比商品更快。</li>
 * </ul>
 *
 * <h3>怎么测</h3>
 * queryPage 要连数据库，所以这里直接测<b>拼装出来的 SQL 片段</b>。
 * 条件拼装是纯逻辑，不需要数据库；而 SQL 片段恰好能同时验证
 * 「筛选加了没有」和「排序落在哪一列」。
 */
class OrderQueryPageTest {

    /**
     * 调<b>生产代码</b>的拼装方法。
     *
     * 【第一版是复刻的，而那是错的】
     * 第一版在这里自己重写了一遍拼装逻辑（理由是 queryPage 要连数据库）。
     * 做正向对照时发现：把生产代码里的 {@code orderByDesc} 整行删掉，
     * 8 条测试<b>全部照常通过</b> —— 因为它们测的是复刻的那一份，
     * 生产代码怎么改都影响不到。
     *
     * 一个永远通过的测试比没有测试更糟：它给的是虚假的安全感。
     * 所以把拼装从 queryPage 里抽成了包内可见的 buildQueryWrapper，
     * 这里直接调它。
     */
    private static QueryWrapper<OrderEntity> build(Map<String, Object> params) {
        return new OrderServiceImpl().buildQueryWrapper(params);
    }

    private static Map<String, Object> params(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("不带任何条件时也必须有确定的排序")
    void noFilterStillOrders() {
        String sql = build(params()).getSqlSegment();
        assertTrue(sql.contains("ORDER BY"),
                "缺少 ORDER BY —— 分页会变成未定义行为：" + sql);
        assertFalse(sql.contains("LIKE"), "没传条件却拼出了模糊匹配：" + sql);
    }

    /**
     * 排序必须<b>唯一确定</b>，也就是最后要落到一个唯一列上。
     *
     * 只按 create_time 排是不够的：秒杀和批量下单会在同一秒里产生大量订单，
     * 这些并列行之间的顺序仍然未定义，翻页时照样重复或丢失。
     * 这正是「看起来加了排序、实际没解决问题」的情况，所以单独守一次。
     */
    @Test
    @DisplayName("排序要以唯一列 id 收尾，否则同一秒下的订单顺序仍然未定义")
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
    @DisplayName("订单号是精确匹配，不是模糊 —— 输错一位不该查到别人的单子")
    void orderSnIsExact() {
        String sql = build(params("orderSn", "202609050001")).getSqlSegment();
        assertTrue(sql.contains("order_sn ="), "订单号应当是精确匹配：" + sql);
        assertFalse(sql.contains("order_sn LIKE"), "订单号不该用模糊匹配：" + sql);
    }

    @Test
    @DisplayName("key 同时匹配收件人、会员名和电话")
    void keySearchesThreeColumns() {
        String sql = build(params("key", "张三")).getSqlSegment();
        assertTrue(sql.contains("receiver_name LIKE"), "没有匹配收件人：" + sql);
        assertTrue(sql.contains("member_username LIKE"), "没有匹配会员名：" + sql);
        assertTrue(sql.contains("receiver_phone LIKE"), "没有匹配电话：" + sql);
    }

    @Test
    @DisplayName("空白参数视同没传 —— 不能拼出把全表都命中的条件")
    void blankParamsAreIgnored() {
        for (String blank : new String[] { "", "   ", "\t" }) {
            String sql = build(params("key", blank, "orderSn", blank, "status", blank)).getSqlSegment();
            assertFalse(sql.contains("LIKE"),
                    "空白参数不该产生 LIKE 条件（输入是 «" + blank.replace("\t", "\\t") + "»）：" + sql);
            assertFalse(sql.contains("order_sn ="), "空白订单号不该产生条件：" + sql);
        }
    }

    @Test
    @DisplayName("status 解析不了时当作没传，而不是抛异常变成 500")
    void unparseableStatusIsIgnored() {
        String sql = build(params("status", "abc")).getSqlSegment();
        // 前端传来一个空的或非法的筛选值是很正常的，不该让整个列表接口 500。
        assertFalse(sql.contains("status ="), "非法 status 不该拼进条件：" + sql);
        assertTrue(sql.contains("ORDER BY"), "即便如此排序也要在：" + sql);
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
     * 一个永远通过的测试比没有测试更糟。这里手工构造一个「退化成原样」的
     * 空 wrapper（生成器模板就是这样），它必须让排序和筛选的断言全部不成立。
     */
    @Test
    @DisplayName("反向对照：退化成空 wrapper 时，上面的断言确实会失败")
    void negativeControl() {
        QueryWrapper<OrderEntity> degraded = new QueryWrapper<>();
        String sql = degraded.getSqlSegment();

        assertFalse(sql.contains("ORDER BY"), "反向对照本身不成立：空 wrapper 不该有 ORDER BY");
        assertFalse(sql.contains("LIKE"), "反向对照本身不成立：空 wrapper 不该有 LIKE");
        assertEquals("", sql.trim(), "空 wrapper 的 SQL 片段应当是空的，实际是：" + sql);
    }
}

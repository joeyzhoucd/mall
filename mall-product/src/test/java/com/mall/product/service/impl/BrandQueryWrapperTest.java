package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.product.entity.BrandEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住品牌分页查询的两个不变量。
 *
 * <h3>为什么这两条值得写测试</h3>
 * 它们都属于「改坏了也编译得过、跑起来也不报错」的那一类：
 *
 * <ol>
 *   <li><b>key 被忽略</b>：原实现传了个空 QueryWrapper，前端传 key 过去
 *       不会有任何错误，只是筛选不生效、返回全量。之所以长期没暴露，
 *       是因为旧后台的品牌页根本没有搜索框。</li>
 *   <li><b>没有 ORDER BY</b>：MySQL 不保证无序查询的行顺序，
 *       所以分页的每一页都是独立的无序查询，行可能重复或漏掉。
 *       数据少于一页时完全正常，等数据一多才出问题，
 *       而那时候没人会联想到是缺了排序。</li>
 * </ol>
 *
 * 测试直接检查生成的 SQL 片段，不连数据库 —— 这样它跑得快，
 * 而且失败信息直接指向拼错的那一段。
 */
class BrandQueryWrapperTest {

    private final BrandServiceImpl service = new BrandServiceImpl();

    private static Map<String, Object> params(String... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 生成的完整 SQL 片段（WHERE 条件 + ORDER BY）。 */
    private static String sql(QueryWrapper<BrandEntity> w) {
        // getCustomSqlSegment 带 WHERE 前缀，getSqlSegment 不带；这里要看排序所以用后者。
        return w.getSqlSegment();
    }

    @Test
    @DisplayName("没有 key 时不加任何筛选条件，但仍然要有确定的排序")
    void noKeyStillOrders() {
        QueryWrapper<BrandEntity> w = service.buildQueryWrapper(params());
        String s = sql(w);

        assertFalse(s.contains("name LIKE"), "没传 key 却加了名称筛选：" + s);
        assertTrue(s.contains("ORDER BY"), "缺少 ORDER BY —— 分页会变成未定义行为：" + s);
    }

    @Test
    @DisplayName("key 会同时匹配 brand_id、name、first_letter")
    void keySearchesThreeColumns() {
        QueryWrapper<BrandEntity> w = service.buildQueryWrapper(params("key", "华为"));
        String s = sql(w);

        assertTrue(s.contains("brand_id ="), "key 没有用于 brand_id 精确匹配：" + s);
        assertTrue(s.contains("name LIKE"), "key 没有用于名称模糊匹配：" + s);
        assertTrue(s.contains("first_letter LIKE"), "key 没有用于首字母匹配：" + s);
        assertTrue(s.contains("OR"), "三个条件之间应该是 OR：" + s);
    }

    @Test
    @DisplayName("空白 key 视同没传，不能拼出 name LIKE '%%' 这种把全表都命中的条件")
    void blankKeyIsIgnored() {
        for (String blank : new String[] { "", "   ", "\t" }) {
            QueryWrapper<BrandEntity> w = service.buildQueryWrapper(params("key", blank));
            assertFalse(sql(w).contains("LIKE"),
                    "空白 key «" + blank.replace("\t", "\\t") + "» 不该产生 LIKE 条件");
        }
    }

    /**
     * 排序必须<b>唯一确定</b>，也就是最后要落到一个唯一列上。
     *
     * 只按 sort 排是不够的：sort 相同的行之间顺序依然未定义，
     * 那些行在翻页时照样会重复或丢失。这一条正是「看起来加了排序、
     * 实际上没解决问题」的情况，所以单独守一次。
     */
    @Test
    @DisplayName("排序要以唯一列 brand_id 收尾，否则同 sort 的行顺序仍然未定义")
    void orderingIsTotal() {
        String s = sql(service.buildQueryWrapper(params()));

        int order = s.indexOf("ORDER BY");
        assertTrue(order >= 0, "没有 ORDER BY：" + s);
        String orderBy = s.substring(order);

        assertTrue(orderBy.contains("sort"), "没有按 sort 排序：" + orderBy);
        assertTrue(orderBy.contains("brand_id"), "排序没有以唯一列 brand_id 收尾：" + orderBy);
        assertTrue(orderBy.indexOf("sort") < orderBy.indexOf("brand_id"),
                "brand_id 应该是次序键，排在 sort 之后：" + orderBy);
    }

    /**
     * 反向对照：确认上面那些断言真的能失败。
     *
     * 一个永远通过的测试比没有测试更糟 —— 它给的是虚假的安全感。
     * 这里手工构造一个「退化成原样」的 wrapper（空条件、无排序），
     * 它必须让上面每一条断言都不成立。
     */
    @Test
    @DisplayName("反向对照：退化成原来的空 wrapper 时，上面的断言确实会失败")
    void negativeControl() {
        QueryWrapper<BrandEntity> degraded = new QueryWrapper<>();
        String s = sql(degraded);

        assertFalse(s.contains("ORDER BY"), "反向对照本身不成立：空 wrapper 不该有 ORDER BY");
        assertFalse(s.contains("LIKE"), "反向对照本身不成立：空 wrapper 不该有 LIKE");
        assertEquals("", s.trim(), "空 wrapper 的 SQL 片段应当是空的，实际是：" + s);
    }
}

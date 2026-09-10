package com.mall.product.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 {@link FilterParams} 的约定：分类/品牌 id 为 0 或缺失表示"不限"。
 *
 * <h3>这一条坏掉的样子：列表永远是空的，而且没有任何报错</h3>
 * 2026-09-10 实测发现规格属性和销售属性两个页面的列表是空的。
 * 原因在 {@code AttrServiceImpl.queryAttrPage}：
 * <pre>
 *   Object categoryId = params.getOrDefault("categoryId", 0L);
 *   wrapper.eq("category_id", categoryId);          // 无条件加等值条件
 *   wrapper.eq(type != null, "attr_type", type);    // 紧下一行用的是带条件的重载
 * </pre>
 * 那两个页面打开时不带 {@code categoryId}（页面上没有分类筛选），于是拼出
 * {@code WHERE category_id = 0} —— 而 {@code pms_attr} 里 {@code category_id}
 * 的取值范围是 3..35，<b>等于 0 的有 0 条</b>。
 *
 * <p>接口返回 {@code code 0}、{@code totalCount 0}，看起来完全像"就是没有数据"。
 * 而库里有 81 条规格属性和 54 条销售属性。同一个方法里相邻两行，
 * 一处写对一处写错，<b>写错的那处不会有任何征兆</b>。
 *
 * <h3>这个测试覆盖的边界，以及它没覆盖的</h3>
 * 覆盖的是那个解析函数的判定：什么样的输入算"不限分类"。
 * <p>
 * <b>没覆盖</b>拼 SQL 那一步 —— {@code queryAttrPage} 里要连数据库，
 * 只能靠集成测试或对着真集群验。所以这个测试保证的是"决定没被改掉"，
 * 不是"整条查询是对的"。这个区别要说清楚，否则它会给人一种虚假的安全感。
 */
class FilterParamsTest {

    @Test
    @DisplayName("0 / 负数 / 空 / 解析不了 都算不限分类（返回 null，条件被跳过）")
    void treatsZeroAndBlankAsUnlimited() {
        // 【这一条就是那个 bug】前端不传 categoryId 时，
        // 原实现取默认值 0 并拼进 WHERE，把列表变成永远为空。
        assertThat(FilterParams.positiveLongOrNull(0L))
                .as("0 是「全部分类」的约定值，不能变成 WHERE category_id = 0")
                .isNull();
        assertThat(FilterParams.positiveLongOrNull("0")).isNull();
        assertThat(FilterParams.positiveLongOrNull(null)).isNull();
        assertThat(FilterParams.positiveLongOrNull("")).isNull();
        assertThat(FilterParams.positiveLongOrNull("   ")).isNull();
        // 负数没有业务含义，同样当不限 —— 而不是拼出一个必然匹配不到的条件
        assertThat(FilterParams.positiveLongOrNull(-1L)).isNull();
        // 解析不了不抛异常：这只是个筛选条件，前端传脏数据不该变成 500
        assertThat(FilterParams.positiveLongOrNull("abc")).isNull();
        assertThat(FilterParams.positiveLongOrNull("3.5")).isNull();
    }

    @Test
    @DisplayName("正数原样返回，String 和 Number 两种形态都要认")
    void keepsPositiveIds() {
        // 走 HTTP 过来的参数是 String
        assertThat(FilterParams.positiveLongOrNull("3")).isEqualTo(3L);
        // 服务内部塞进 map 的是 Long
        assertThat(FilterParams.positiveLongOrNull(35L)).isEqualTo(35L);
        // Integer 也是 Number
        assertThat(FilterParams.positiveLongOrNull(12)).isEqualTo(12L);
        // 前后有空白（表单直传）
        assertThat(FilterParams.positiveLongOrNull(" 7 ")).isEqualTo(7L);
        // pms_attr 里 category_id 的真实取值范围是 3..35，两端都要过
        assertThat(FilterParams.positiveLongOrNull(3L)).isEqualTo(3L);
        assertThat(FilterParams.positiveLongOrNull(35L)).isEqualTo(35L);
    }
}

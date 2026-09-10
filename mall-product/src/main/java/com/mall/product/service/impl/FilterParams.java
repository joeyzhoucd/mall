package com.mall.product.service.impl;

/**
 * 列表筛选参数的解析。
 *
 * <h3>为什么要有这么一个类：同一个约定在这个模块里有三种写法</h3>
 * 后台列表页的筛选框都遵循一个约定：<b>{@code 0} 表示"全部/不限"</b>
 * （gulimall 沿用下来的，前端下拉的"全部"选项就传 0）。
 * 而 2026-09-10 查下来，mall-product 里这个约定有三种实现：
 * <ul>
 *   <li>{@code SpoInfoServiceImpl}：{@code if (id != null && id > 0)} —— <b>对的</b></li>
 *   <li>{@code SkuInfoServiceImpl}：只判 {@code != null} —— 潜在的坑，
 *       靠调用方恰好不传 0 才没炸</li>
 *   <li>{@code AttrServiceImpl}：{@code getOrDefault(..., 0L)} 之后无条件拼条件
 *       —— <b>真的炸了</b>：规格属性和销售属性两页的列表一直是空的</li>
 * </ul>
 *
 * <h3>那个 bug 长什么样：完全没有征兆</h3>
 * 前端不传 categoryId 时取默认值 0，拼出 {@code WHERE category_id = 0}，
 * 而 {@code pms_attr} 里 {@code category_id} 的取值范围是 3..35、等于 0 的有 0 条。
 * 接口返回 {@code code 0} / {@code totalCount 0}，看起来完全像"就是没有数据" ——
 * 没有异常、没有日志。库里其实有 81 条规格属性和 54 条销售属性。
 *
 * <p>所以这个约定值得有一个<b>唯一</b>的实现，而不是每处各写一遍：
 * 各写一遍必然漂移，而漂移的方向恰好是"某个页面永远空白"。
 *
 * <h3>为什么不放 mall-common</h3>
 * 那里是更自然的位置（别的服务也有同样的筛选），但 mall-common 是几乎所有服务的
 * 依赖 —— 动它会让 CI 从"重建 1 个镜像"变成"重建 11 个"。
 * 等到第二个模块真的需要时再上移，那时这个类已经有测试了。
 */
final class FilterParams {

    private FilterParams() {
    }

    /**
     * 把筛选参数解析成"正数或 null"，交给 {@code wrapper.eq(condition, ...)} 决定加不加条件。
     *
     * <p>请求参数是 {@code Map<String, Object>}，同一个键可能是 String（走 HTTP 来的）
     * 也可能是 Long（服务内部塞进去的），所以两种都要认。
     *
     * <p><b>0、负数、空串、解析不出来 一律返回 null（= 不限）。</b>
     * 解析失败刻意不抛异常：这只是一个筛选条件，前端传个空串就变成 500 是不合理的
     * （和 {@code OrderServiceImpl.parseLong} 的取舍一致）。
     */
    static Long positiveLongOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        long v;
        if (raw instanceof Number number) {
            v = number.longValue();
        } else {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                v = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return v > 0 ? v : null;
    }
}

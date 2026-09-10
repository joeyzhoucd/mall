package com.mall.product.vo;

import lombok.Data;

/**
 * 属性分组的列表响应。
 *
 * <h3>为什么不直接返回 {@code AttrGroupEntity}</h3>
 * 实体上只有 {@code categoryId}，没有分类名。前端因此只能显示 {@code #3} 这种
 * 原始 id（attr-group.tsx 里为此留了注释："这个接口只返回 categoryId，
 * 不返回分类名。显示 id 而不是去逐行查名字"）。
 *
 * <p>那个取舍在"要么 N+1 要么显示 id"之间选了后者，是对的 ——
 * 但第三个选项更好：<b>一次批量查询把分类名带上</b>。
 * 同模块的 {@link AttrResponseVO} 早就有 {@code categoryName} 这个字段，
 * 只是它那边是逐行 {@code selectById}（真正的 N+1）。这里不照抄那个写法。
 *
 * <p>2026-09-10：界面上 27 个分组全叫"基本参数"、排序全 0、描述全空 ——
 * 那是<b>种子数据本身</b>的样子（{@code gen-taxonomy.js} 给每个分类各造了一个
 * 同名分组），不是这一层的问题。补上分类名之后至少能看出它们分属不同分类。
 */
@Data
public class AttrGroupResponseVO {

    private Long attrGroupId;

    private String attrGroupName;

    private Integer sort;

    private String descript;

    private String icon;

    private Long categoryId;

    /**
     * 所属分类名。
     *
     * <p>分类被删掉时会是 null —— 前端要能接住这种情况（回落显示 categoryId），
     * 而不是显示成空白。分组挂在不存在的分类下本身是脏数据，
     * 但它不该表现成"这一行什么都没有"。
     */
    private String categoryName;
}

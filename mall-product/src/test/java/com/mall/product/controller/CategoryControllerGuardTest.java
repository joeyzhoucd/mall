package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 守住 CategoryController 上两个「改坏了也不报错」的地方。
 *
 * <h3>1. 拖拽保存必须是 UPDATE，不能是 INSERT</h3>
 * 原实现是 {@code categoryService.saveBatch(categories)}。调用方发的是
 * <b>已存在</b>的分类（每条都带 catId），而 saveBatch 是插入语义，
 * CategoryEntity 的 @TableId 没指定策略、项目也没配 id-type，
 * 走 MyBatis-Plus 默认的 ASSIGN_ID —— 它会带上传入的 id 去 INSERT，必然主键冲突。
 * 也就是说：这个接口从上线起就没成功过一次。
 * <p>
 * 这条测试直接验证「调的是 updateBatchById 而不是 saveBatch」，
 * 因为这正是当初写错的那一步，也是最容易被「顺手改回去」的一步。
 *
 * <h3>2. 删除分类不能留下孤儿</h3>
 * 分类是逻辑删除，删父分类时子分类一条都不动，parentCid 仍指向已删除的父节点。
 * 树是从 parentCid=0 递归拼的，于是这些子分类<b>从界面上整个消失</b>，
 * 但数据还在、商品仍挂在上面 —— 没有任何报错，只是「分类不见了」。
 * <p>
 * 真正的把关在 service 的分布式锁里面（锁外面查了也白查），
 * 控制器负责把异常翻成人能看懂的响应。这条测试守的是后半段。
 */
class CategoryControllerGuardTest {

    private CategoryService service;
    private CategoryController controller;

    @BeforeEach
    void setUp() {
        service = mock(CategoryService.class);
        controller = new CategoryController();
        ReflectionTestUtils.setField(controller, "categoryService", service);
    }

    private static CategoryEntity cat(Long id, Long parent, int level, int sort) {
        CategoryEntity c = new CategoryEntity();
        c.setCatId(id);
        c.setParentCid(parent);
        c.setCatLevel(level);
        c.setSort(sort);
        return c;
    }

    // ---------------------------------------------------------------- 拖拽保存

    @Test
    @DisplayName("拖拽保存走 updateBatchById，绝不能走 saveBatch")
    void dragSaveUpdatesRatherThanInserts() {
        List<CategoryEntity> payload = Arrays.asList(cat(5L, 2L, 2, 1), cat(9L, 2L, 2, 2));

        R r = controller.saveDrag(payload);

        assertEquals(0, r.get("code"), "正常的拖拽保存应该成功");
        verify(service).updateBatchById(payload);
        // 这一条才是这个测试存在的理由：saveBatch 是插入语义，
        // 拿它保存已有分类必然主键冲突。
        verify(service, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("有元素缺 catId 时拒绝，且一行都不写")
    void dragSaveRejectsMissingId() {
        List<CategoryEntity> payload = Arrays.asList(cat(5L, 2L, 2, 1), cat(null, 2L, 2, 2));

        R r = controller.saveDrag(payload);

        assertNotEquals(0, r.get("code"), "缺 catId 应该被拒绝");
        assertTrue(String.valueOf(r.get("msg")).contains("catId"),
                "错误信息要说清楚是 catId 的问题，实际是：" + r.get("msg"));
        // 拒绝之后一行都不能写 —— 部分写入比整体失败更难排查。
        verify(service, never()).updateBatchById(anyList());
        verify(service, never()).saveBatch(anyList());
    }

    @Test
    @DisplayName("空数组直接成功返回，不打扰 service")
    void dragSaveIgnoresEmpty() {
        assertEquals(0, controller.saveDrag(Collections.emptyList()).get("code"));
        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------ 删除

    @Test
    @DisplayName("service 拒绝删除时，把原因原样带给调用方")
    void deleteSurfacesTheReason() {
        doThrow(new IllegalStateException("有 4 个子分类挂在待删除的分类下面，请先处理子分类"))
                .when(service).removeByIds(any());

        R r = controller.delete(new Long[] { 2L });

        assertNotEquals(0, r.get("code"), "被拒绝的删除不能返回成功");
        assertTrue(String.valueOf(r.get("msg")).contains("4 个子分类"),
                "要把具体数量带出去，「删除失败」四个字帮不上忙，实际是：" + r.get("msg"));
    }

    @Test
    @DisplayName("空的 catIds 不调用 service —— 别把「没选中任何东西」变成一次全表操作")
    void deleteIgnoresEmpty() {
        assertEquals(0, controller.delete(new Long[0]).get("code"));
        assertEquals(0, controller.delete(null).get("code"));
        verifyNoInteractions(service);
    }
}

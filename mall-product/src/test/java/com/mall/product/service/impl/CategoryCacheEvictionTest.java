package com.mall.product.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住「分类的每一个写操作都要清缓存」。
 *
 * <h3>这条规则是怎么被撞出来的</h3>
 * listAsTree() 是 @Cacheable 的。save / saveBatch / updateById / removeByIds
 * 都重写并加了 @CacheEvict，唯独 updateBatchById 没有 —— 因为它一直没被用到。
 *
 * 2026-09-04 把 /product/category/save/drag 从 saveBatch（INSERT，必然主键冲突）
 * 改成 updateBatchById 之后，这个洞立刻暴露，而且形式最坏：
 * <b>接口返回 success，数据也确实写进了数据库，但树接口返回的还是旧值</b>。
 * 实测：把 sort 改成 42 之后，不走缓存的 /product/category/info/{id} 返回 42，
 * 而 @Cacheable 的 /product/category/list/tree 仍然返回 100。
 *
 * 「报错但不写」至少还会报错。「写了但看不见」不报错，
 * 用户只会觉得「保存按钮坏了」，而日志里一切正常。
 *
 * <h3>为什么用反射扫而不是逐个方法写断言</h3>
 * 逐个写的话，下一个人新增一个写方法时不会有任何测试失败 ——
 * 而那正是这次出问题的原因。按命名规则扫，新方法一加进来就自动被覆盖。
 */
class CategoryCacheEvictionTest {

    /** 会改动分类数据的方法名前缀。 */
    private static final List<String> WRITE_PREFIXES = Arrays.asList("save", "update", "remove");

    private static List<Method> declaredWriteMethods() {
        return Arrays.stream(CategoryServiceImpl.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> WRITE_PREFIXES.stream().anyMatch(p -> m.getName().startsWith(p)))
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("CategoryServiceImpl 里每一个写方法都必须带 @CacheEvict")
    void everyWriteMethodEvictsTheCache() {
        List<String> missing = new ArrayList<>();
        for (Method m : declaredWriteMethods()) {
            if (m.getAnnotation(CacheEvict.class) == null) {
                missing.add(m.getName());
            }
        }
        assertTrue(
                missing.isEmpty(),
                "这些写方法没有 @CacheEvict，改完之后 list/tree 会继续返回旧数据"
                        + "（接口返回成功、数据库也写了，但界面上看不到）：" + missing
        );
    }

    /**
     * 反向对照：确认上面那条断言真的在检查东西。
     *
     * 如果 declaredWriteMethods() 因为命名规则变化而扫不到任何方法，
     * 上面的测试会「因为没有反例」而通过 —— 那是虚假的安全感。
     * 这里锁住「至少扫到了预期的那几个」。
     */
    @Test
    @DisplayName("反向对照：扫描确实找到了那几个写方法，不是扫了个空")
    void scannerActuallyFindsMethods() {
        List<String> names = declaredWriteMethods().stream()
                .map(Method::getName).distinct().sorted().collect(Collectors.toList());

        assertFalse(names.isEmpty(), "一个写方法都没扫到，说明扫描规则失效了");
        for (String expected : Arrays.asList("save", "saveBatch", "updateById", "updateBatchById", "removeByIds")) {
            assertTrue(names.contains(expected),
                    "没扫到 " + expected + "，扫描规则和实际代码对不上了。扫到的是：" + names);
        }
    }

    @Test
    @DisplayName("listAsTree 仍然是 @Cacheable —— 上面那条规则的前提")
    void treeIsStillCached() throws NoSuchMethodException {
        Method tree = CategoryServiceImpl.class.getDeclaredMethod("listAsTree");
        assertTrue(
                tree.getAnnotation(Cacheable.class) != null,
                "listAsTree 不再有 @Cacheable 的话，@CacheEvict 那条规则就失去意义了，"
                        + "这个测试类也该跟着调整而不是留着装样子"
        );
    }
}

package com.mall.product.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryCacheEvictionTest {

    private static final List<String> WRITE_PREFIXES = Arrays.asList("save", "update", "remove");
    private static final Path SOURCE = Path.of(
            "src/main/java/com/mall/product/service/impl/CategoryServiceImpl.java");

    private static List<Method> declaredWriteMethods() {
        return Arrays.stream(CategoryServiceImpl.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> WRITE_PREFIXES.stream().anyMatch(p -> m.getName().startsWith(p)))
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("CategoryServiceImpl 里的每一个写方法都必须提交后清理多级缓存")
    void everyWriteMethodEvictsTheMultiLevelCacheAfterCommit() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (Method method : declaredWriteMethods()) {
            String body = methodBody(source, method.getName());
            if (!body.contains("evictCategoryTreeCacheAfterCommit();")) {
                missing.add(method.getName());
            }
        }

        assertTrue(missing.isEmpty(),
                "这些写方法没有清理分类树多级缓存，写成功后 list/tree 可能继续返回旧树：" + missing);
    }

    @Test
    @DisplayName("反向对照：扫描确实找到了那几个写方法，不是扫了个空")
    void scannerActuallyFindsMethods() {
        List<String> names = declaredWriteMethods().stream()
                .map(Method::getName).distinct().sorted().collect(Collectors.toList());

        assertFalse(names.isEmpty(), "一个写方法都没扫到，说明扫描规则失效了");
        for (String expected : Arrays.asList("save", "saveBatch", "updateById", "updateBatchById", "removeByIds")) {
            assertTrue(names.contains(expected),
                    "没扫到 " + expected + "，扫描规则和实际代码对不上。扫到的是：" + names);
        }
    }

    @Test
    @DisplayName("listAsTree 走 MultiLevelCacheClient，不再走 Spring Cache 注解")
    void treeUsesMultiLevelCacheClient() throws NoSuchMethodException, IOException {
        Method tree = CategoryServiceImpl.class.getDeclaredMethod("listAsTree");
        assertTrue(tree.getAnnotation(Cacheable.class) == null, "listAsTree 不应再使用 @Cacheable");
        assertTrue(tree.getAnnotation(CacheEvict.class) == null, "listAsTree 不应使用 @CacheEvict");

        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        String body = methodBody(source, "listAsTree");
        assertTrue(body.contains("multiLevelCacheClient.get(CATEGORY_CACHE_NAME, CATEGORY_TREE_CACHE_KEY"),
                "listAsTree 必须通过 MultiLevelCacheClient 进入 Caffeine + Redis 多级缓存");
    }

    private static String methodBody(String source, String methodName) {
        String marker = "public ";
        int start = source.indexOf(methodName + "(");
        assertTrue(start >= 0, "源码里找不到方法：" + methodName);
        start = source.lastIndexOf(marker, start);
        assertTrue(start >= 0, "源码里找不到 public 方法声明：" + methodName);

        int next = source.indexOf("\n    public ", start + marker.length());
        if (next < 0) {
            next = source.length();
        }
        return source.substring(start, next);
    }
}

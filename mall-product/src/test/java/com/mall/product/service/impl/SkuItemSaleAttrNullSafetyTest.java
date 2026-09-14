package com.mall.product.service.impl;

import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.product.dao.AttrGroupDao;
import com.mall.product.dao.SkuImagesDao;
import com.mall.product.dao.SkuInfoDao;
import com.mall.product.dao.SkuSaleAttrValueDao;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.entity.SpuInfoDescEntity;
import com.mall.product.service.SpuInfoDescService;
import com.mall.product.vo.SkuItemSaleAttrVo;
import com.mall.product.vo.SkuItemVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 守住「商品详情页的销售属性列表里不能有 null 元素」。
 *
 * <h3>这个测试对应一个真实的 500（2026-09-14 修）</h3>
 * 症状：{@code http://item.mall.com/1001.html} 直接 500。
 * 日志里的真因：
 * <pre>
 *   EL1007E: Property or field 'attrName' cannot be found on null
 *   (template: "item" - line 85, col 41)   &lt;-- th:each="attr : ${item.saleAttr}"
 * </pre>
 * 也就是 {@code saleAttr} 这个 List 里有一个 <b>null 元素</b>。
 *
 * <h3>null 是怎么进去的</h3>
 * mapper 里是 {@code LEFT JOIN pms_sku_sale_attr_value}。
 * 某个 SKU 在销售属性表里一行都没有时（实测 sku 1001~1004 都是这样，
 * 因为它们是单规格商品，本来就没有颜色/尺码可选），
 * join 出来是一行 {@code attr_id/attr_name/attr_value/sku_ids} 全 NULL，
 * GROUP BY 把它聚成一个「全 NULL 的分组」。
 * <p>
 * 而 MyBatis 的默认行为是：一行里被映射的列<b>全为 NULL</b> 时，
 * 返回 <b>null</b> 而不是一个字段都为 null 的空对象
 * （对应设置 {@code returnInstanceForEmptyRow}，默认 false）。
 * 于是 List 里凭空多出一个 null。
 *
 * <h3>为什么这个 bug 能一直躲着</h3>
 * 异常是在 <b>Thymeleaf 视图渲染阶段</b>抛的 —— 那时候
 * {@code @ExceptionHandler} 的作用域已经结束，全局异常兜底<b>捕获不到</b>，
 * 日志里只看得见 /error 上的二次异常（{@code HttpMessageNotWritableException}），
 * 完全不指向真实原因。
 * <p>
 * 这也是为什么这里要用测试把不变量钉死：这条链路上没有别的安全网。
 */
class SkuItemSaleAttrNullSafetyTest {

    private SkuInfoServiceImpl service;
    private SkuSaleAttrValueDao saleAttrDao;
    private ExecutorService delegate;

    @BeforeEach
    void setUp() {
        service = new SkuInfoServiceImpl();

        // 缓存一律直接调 loader = 每次都未命中，走真实装配路径
        MultiLevelCacheClient cache = mock(MultiLevelCacheClient.class);
        when(cache.get(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());

        SkuInfoDao skuInfoDao = mock(SkuInfoDao.class);
        SkuInfoEntity info = new SkuInfoEntity();
        info.setSkuId(1001L);
        info.setSpuId(1L);
        info.setCategoryId(100L);
        when(skuInfoDao.selectById(anyLong())).thenReturn(info);

        SkuImagesDao skuImagesDao = mock(SkuImagesDao.class);
        when(skuImagesDao.selectList(any())).thenReturn(List.of());

        saleAttrDao = mock(SkuSaleAttrValueDao.class);

        AttrGroupDao attrGroupDao = mock(AttrGroupDao.class);
        when(attrGroupDao.getAttrGroupWithAttrsBySpuId(anyLong(), anyLong())).thenReturn(List.of());

        SpuInfoDescService descService = mock(SpuInfoDescService.class);
        when(descService.getById(anyLong())).thenReturn(new SpuInfoDescEntity());

        delegate = Executors.newFixedThreadPool(4);
        AsyncTaskExecutor executor = delegate::execute;

        ReflectionTestUtils.setField(service, "multiLevelCacheClient", cache);
        ReflectionTestUtils.setField(service, "baseMapper", skuInfoDao);
        ReflectionTestUtils.setField(service, "skuImagesDao", skuImagesDao);
        ReflectionTestUtils.setField(service, "skuSaleAttrValueDao", saleAttrDao);
        ReflectionTestUtils.setField(service, "attrGroupDao", attrGroupDao);
        ReflectionTestUtils.setField(service, "spuInfoDescService", descService);
        ReflectionTestUtils.setField(service, "applicationTaskExecutor", executor);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        delegate.shutdown();
        delegate.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * <b>本文件的核心断言。</b>
     *
     * <p>这里让 DAO 返回一个含 null 的列表 —— 这正是修复前 MyBatis 给出的东西，
     * <b>也是已经写进缓存的东西</b>。
     * <p>
     * 后一点很关键：只把 mapper 改成 INNER JOIN 是不够的，
     * 旧的坏列表已经躺在本地缓存和 Redis 里，会继续 500 直到 TTL 到期 ——
     * 也就是「代码修好了但线上还在坏」。所以过滤必须在缓存<b>读出口</b>。
     */
    @Test
    @DisplayName("销售属性列表里的 null 元素必须被过滤掉 —— 它会让详情页 500")
    void nullElementsAreFilteredOut() {
        List<SkuItemSaleAttrVo> poisoned = new ArrayList<>();
        poisoned.add(null);                 // 修复前的 LEFT JOIN 全 NULL 行
        poisoned.add(saleAttr("颜色", 1L));
        poisoned.add(null);
        when(saleAttrDao.getSaleAttrsBySpuId(anyLong())).thenReturn(poisoned);

        SkuItemVo vo = service.item(1001L);

        assertNotNull(vo.getSaleAttr(), "saleAttr 不该为 null");
        assertFalse(vo.getSaleAttr().contains(null),
                "saleAttr 里还有 null 元素 —— 模板 th:each 迭代到它就是 "
                        + "EL1007E: Property or field 'attrName' cannot be found on null，"
                        + "而且这个异常在视图渲染阶段抛，全局兜底捕获不到");
        assertEquals(1, vo.getSaleAttr().size(), "非 null 的那一项应该留下");
        assertEquals("颜色", vo.getSaleAttr().get(0).getAttrName());
    }

    /**
     * 负控：没有 null 时不能把好数据也弄丢。
     * 没有这条的话，「全部返回空列表」也能让上面那条通过。
     */
    @Test
    @DisplayName("负控：列表里没有 null 时，数据要原样保留")
    void cleanListIsPreservedUnchanged() {
        when(saleAttrDao.getSaleAttrsBySpuId(anyLong()))
                .thenReturn(Arrays.asList(saleAttr("颜色", 1L), saleAttr("尺码", 2L)));

        SkuItemVo vo = service.item(1001L);

        assertEquals(2, vo.getSaleAttr().size());
        assertEquals("颜色", vo.getSaleAttr().get(0).getAttrName());
        assertEquals("尺码", vo.getSaleAttr().get(1).getAttrName());
    }

    /**
     * 单规格商品（没有任何销售属性）是<b>合法</b>的，
     * 应该得到空列表而不是异常 —— 模板的 th:each 遇到空列表什么都不渲染。
     */
    @Test
    @DisplayName("没有销售属性的商品要得到空列表，不能是 null")
    void noSaleAttrsYieldsEmptyList() {
        when(saleAttrDao.getSaleAttrsBySpuId(anyLong())).thenReturn(List.of());

        SkuItemVo vo = service.item(1001L);

        assertNotNull(vo.getSaleAttr(), "空列表，不是 null —— 模板要能安全迭代");
        assertTrue(vo.getSaleAttr().isEmpty());
    }

    /**
     * 守根因。
     *
     * <p>上面那几条守的是「就算 null 进来了也不会炸」，这条守的是「null 不该进来」。
     * 两层都要有：只有过滤的话，下次有人把 JOIN 改回去，
     * 症状会变成「属性莫名少一个」而不是 500，更难发现。
     * <p>
     * 用扫文本而不是跑 SQL，是因为跑 SQL 需要一个 MySQL 容器，
     * 而这条断言的成本应该低到每次构建都跑。
     */
    @Test
    @DisplayName("mapper 里对销售属性表不能用 LEFT JOIN —— 那会造出全 NULL 行")
    void mapperMustNotLeftJoinSaleAttrTable() throws Exception {
        String xml;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("mapper/product/SkuSaleAttrValueDao.xml")) {
            assertNotNull(in, "找不到 mapper XML —— 这条断言本身失效了，先修探针");
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // 自检：探针必须真的读到了那条 SQL，否则下面的"没找到 LEFT JOIN"毫无意义
        assertTrue(xml.contains("getSaleAttrsBySpuId"),
                "读到的 XML 里没有 getSaleAttrsBySpuId —— 探针读错文件了");

        // 只看 SQL 正文，注释里是允许提 LEFT JOIN 的（那里正解释着这个坑）
        String sql = xml.substring(xml.indexOf("getSaleAttrsBySpuId"));
        sql = sql.substring(0, sql.indexOf("</select>"));

        assertFalse(sql.toUpperCase().contains("LEFT JOIN"),
                "getSaleAttrsBySpuId 又用上 LEFT JOIN 了：SKU 没有销售属性行时会 join 出"
                        + "一行全 NULL，MyBatis 把它映射成 null 塞进列表，详情页 500");
        assertTrue(sql.toUpperCase().contains("INNER JOIN"),
                "预期是 INNER JOIN：这个查询要的就是属性行本身，没有属性行就该返回空");
    }

    private static SkuItemSaleAttrVo saleAttr(String name, Long attrId) {
        SkuItemSaleAttrVo vo = new SkuItemSaleAttrVo();
        vo.setAttrId(attrId);
        vo.setAttrName(name);
        vo.setAttrValues(List.of());
        return vo;
    }
}

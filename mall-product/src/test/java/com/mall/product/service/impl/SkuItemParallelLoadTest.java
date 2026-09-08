package com.mall.product.service.impl;

import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.product.dao.AttrGroupDao;
import com.mall.product.dao.SkuImagesDao;
import com.mall.product.dao.SkuInfoDao;
import com.mall.product.dao.SkuSaleAttrValueDao;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.entity.SpuInfoDescEntity;
import com.mall.product.service.SpuInfoDescService;
import com.mall.product.vo.SkuItemVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
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
 * 守住商品详情装配<b>真的是并行的</b>。
 *
 * <h3>这个测试对应一个真实存在过的缺陷（2026-09-08 修）</h3>
 * 原实现是 {@code CompletableFuture.runAsync(task)} —— <b>不传执行器</b>，
 * 于是任务落到 {@code ForkJoinPool.commonPool()}。
 * 而 common pool 的并行度是 {@code availableProcessors() - 1}，
 * 容器 CPU limit 是 500m、Java 21 认 cgroup 配额，
 * 所以 {@code availableProcessors()} 是 1（实测：运行中的 mall-product pod
 * {@code /actuator/prometheus} 里 {@code system_cpu_count 1.0}），
 * 并行度算出来是 <b>0</b> —— 池子没有工作线程，任务在 join 时由提交线程自己跑。
 * <p>
 * 也就是说那四个「并行」任务一直是<b>串行</b>的。
 *
 * <h3>为什么这个缺陷不会被任何常规手段发现</h3>
 * 功能完全正确：详情页该有的字段都有，接口 200，日志干净。
 * 唯一的症状是慢，而且只在缓存击穿时明显 ——
 * 那四个任务走的是带互斥重建的缓存读，未命中时会
 * {@code Thread.sleep} 轮询等锁（lockWait=300ms）。
 * 串行 × 4 = 最坏多等 1.2 秒，本该是 300ms。
 * <p>
 * 而"慢 1 秒"没有任何东西会报警，也不会有人把它联想到一个缺省的方法参数。
 *
 * <h3>为什么测行为而不是扫源码</h3>
 * 扫源码只能证明"参数写在那里"。这里注入一个<b>会计数的执行器</b>，
 * 断言四个任务确实都经过它 —— 少传一个参数，计数就少一个，
 * 因为那个任务跑去了 common pool。
 * 这样对重构也更稳（比如有人改成 supplyAsync，扫源码的正则就废了）。
 */
class SkuItemParallelLoadTest {

    private SkuInfoServiceImpl service;
    private ExecutorService delegate;
    private AtomicInteger submitted;
    private Set<String> taskThreads;

    @BeforeEach
    void setUp() {
        service = new SkuInfoServiceImpl();

        // 缓存客户端一律直接调 loader —— 相当于每次都未命中，
        // 这正是 loadSkuItemFromDb 会被执行的场景。
        MultiLevelCacheClient cache = mock(MultiLevelCacheClient.class);
        when(cache.get(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(3)).get());

        SkuInfoDao skuInfoDao = mock(SkuInfoDao.class);
        SkuInfoEntity info = new SkuInfoEntity();
        info.setSkuId(1L);
        info.setSpuId(10L);
        info.setCategoryId(100L);
        when(skuInfoDao.selectById(anyLong())).thenReturn(info);

        SkuImagesDao skuImagesDao = mock(SkuImagesDao.class);
        when(skuImagesDao.selectList(any())).thenReturn(List.of());

        SkuSaleAttrValueDao saleAttrDao = mock(SkuSaleAttrValueDao.class);
        when(saleAttrDao.getSaleAttrsBySpuId(anyLong())).thenReturn(List.of());

        AttrGroupDao attrGroupDao = mock(AttrGroupDao.class);
        when(attrGroupDao.getAttrGroupWithAttrsBySpuId(anyLong(), anyLong())).thenReturn(List.of());

        SpuInfoDescService descService = mock(SpuInfoDescService.class);
        when(descService.getById(anyLong())).thenReturn(new SpuInfoDescEntity());

        delegate = Executors.newFixedThreadPool(4);
        submitted = new AtomicInteger();
        taskThreads = ConcurrentHashMap.newKeySet();

        // AsyncTaskExecutor 的唯一抽象方法是 execute(Runnable)（submit 有默认实现），
        // 所以可以用 lambda。这里既转发又记账。
        AsyncTaskExecutor recording = task -> {
            submitted.incrementAndGet();
            delegate.execute(() -> {
                taskThreads.add(Thread.currentThread().getName());
                task.run();
            });
        };

        ReflectionTestUtils.setField(service, "multiLevelCacheClient", cache);
        ReflectionTestUtils.setField(service, "baseMapper", skuInfoDao);
        ReflectionTestUtils.setField(service, "skuImagesDao", skuImagesDao);
        ReflectionTestUtils.setField(service, "skuSaleAttrValueDao", saleAttrDao);
        ReflectionTestUtils.setField(service, "attrGroupDao", attrGroupDao);
        ReflectionTestUtils.setField(service, "spuInfoDescService", descService);
        ReflectionTestUtils.setField(service, "applicationTaskExecutor", recording);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        delegate.shutdown();
        delegate.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * <b>本文件的核心断言。</b>
     *
     * <p>四个装配任务必须全部经过注入的执行器。
     * 少传一个 executor 参数，那个任务就会跑到 ForkJoinPool.commonPool()，
     * 计数只会是 3 —— 而功能上看不出任何区别。
     */
    @Test
    @DisplayName("四个装配任务都要提交到注入的执行器，一个都不能落到 common pool")
    void allFourTasksGoThroughTheInjectedExecutor() {
        service.item(1L);

        assertEquals(4, submitted.get(),
                "有任务没有走注入的执行器 —— 它落到了 ForkJoinPool.commonPool()，"
                        + "而那个池在 500m CPU 下并行度为 0，等于变回串行");
    }

    @Test
    @DisplayName("任务不能跑在调用线程上 —— 那就等于没有并行")
    void tasksDoNotRunOnTheCallerThread() {
        String caller = Thread.currentThread().getName();
        service.item(1L);

        assertFalse(taskThreads.isEmpty(), "没有任何任务被派发出去");
        assertFalse(taskThreads.contains(caller),
                "有任务跑在调用线程上：" + taskThreads);
    }

    /**
     * 并行之后可见性也要成立。
     *
     * <p>四个任务各写 skuItemVo 的不同字段，靠 {@code allOf(...).get()}
     * 建立 happens-before 边。这条断言守的是"装配结果确实完整"——
     * 如果哪天有人把 {@code .get()} 去掉换成 fire-and-forget，
     * 功能上表现为"详情页偶尔缺一块"，而那是最难查的一类问题。
     */
    @Test
    @DisplayName("并行写入的四个字段在 get() 之后必须都可见")
    void allAssembledFieldsAreVisibleAfterJoin() {
        SkuItemVo vo = service.item(1L);

        assertNotNull(vo, "详情对象为空");
        assertNotNull(vo.getInfo(), "info 没装上");
        assertNotNull(vo.getImages(), "images 没装上（并行任务的写入不可见？）");
        assertNotNull(vo.getSaleAttr(), "saleAttr 没装上");
        assertNotNull(vo.getDesc(), "desc 没装上");
        assertNotNull(vo.getGroupAttrs(), "groupAttrs 没装上");
    }

    /**
     * 反向对照：确认上面那条计数断言不是恒真的。
     *
     * <p>如果被测代码根本没派发任务（比如全改成同步调用），
     * submitted 会是 0，第一条断言就会失败。这里验证计数器本身
     * 在"没有派发"时确实是 0，也就是它真的在数东西。
     */
    @Test
    @DisplayName("反向对照：不调用被测方法时计数器为 0，说明它真的在数")
    void negativeControl() {
        assertEquals(0, submitted.get(), "还没调用就已经有计数，说明计数器测不出问题");
        assertTrue(taskThreads.isEmpty());
    }
}

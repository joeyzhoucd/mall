package com.mall.ware.service.impl;

import com.mall.ware.dao.PurchaseDao;
import com.mall.ware.dao.PurchaseDetailDao;
import com.mall.ware.entity.PurchaseDetailEntity;
import com.mall.ware.service.WareSkuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住「完成采购」不会重复入库。
 *
 * <h3>为什么这条值得单独一个测试类</h3>
 * {@code finish()} 会对每条成功明细调用 {@code wareSkuService.addStock()}。
 * 原实现<b>不检查明细是否已经完成过</b>，所以对同一张采购单调两次 finish，
 * 库存就加两遍。
 *
 * 这里有个容易被误解的地方：finish 是 {@code @Transactional} 的，
 * 但事务只保证「<b>一次</b>调用要么全成要么全不成」，
 * 它<b>挡不住第二次调用</b> —— 两次成功调用各自提交，库存加两遍。
 * 所以「有事务」不等于「幂等」，这两件事经常被混为一谈。
 *
 * 现实里触发它太容易了：界面上双击「完成」、网络超时后重试、两个人同时点。
 * 而结果是仓库库存<b>虚增且没有任何报错</b> ——
 * 要等到盘点对不上、或者按虚假库存卖出去导致超卖时才会发现。
 *
 * <h3>测试放在 impl 包里是有意的</h3>
 * 幂等判断依赖 DETAIL_FINISHED 这些状态常量，它们是包内可见的
 * （只给实现和它的测试用，不该变成对外 API）。
 * 放在 com.mall.ware.service 下会编译不过 —— 第一版就是这么写的。
 */
class PurchaseFinishIdempotencyTest {

    private PurchaseServiceImpl service;
    private PurchaseDetailDao detailDao;
    private WareSkuService wareSkuService;

    private static final long PURCHASE_ID = 100L;

    @BeforeEach
    void setUp() {
        detailDao = mock(PurchaseDetailDao.class);
        wareSkuService = mock(WareSkuService.class);
        service = new PurchaseServiceImpl();
        ReflectionTestUtils.setField(service, "purchaseDetailDao", detailDao);
        ReflectionTestUtils.setField(service, "wareSkuService", wareSkuService);
        // finish 末尾会 this.updateById(采购单)，那是 ServiceImpl 的方法、需要 baseMapper。
        // 这里只关心明细和库存那一段，所以把它挡掉。
        ReflectionTestUtils.setField(service, "baseMapper", mock(PurchaseDao.class));
    }

    private PurchaseDetailEntity detail(long id, int status, long purchaseId) {
        PurchaseDetailEntity d = new PurchaseDetailEntity();
        d.setId(id);
        d.setPurchaseId(purchaseId);
        d.setSkuId(2001L);
        d.setWareId(1L);
        d.setSkuNum(10);
        d.setStatus(status);
        return d;
    }

    @Test
    @DisplayName("正常完成：未完成的明细会入库一次")
    void addsStockOnce() {
        when(detailDao.selectById(1L))
                .thenReturn(detail(1L, PurchaseServiceImpl.DETAIL_RECEIVED, PURCHASE_ID));

        service.finish(PURCHASE_ID, List.of(1L), Collections.emptyList());

        verify(wareSkuService, times(1)).addStock(eq(2001L), eq(1L), eq(10), any());
    }

    @Test
    @DisplayName("已完成的明细再次完成时【不能】再入库一次 —— 这是本类存在的理由")
    void doesNotAddStockTwice() {
        // 第二次调用时，这条明细在库里已经是「已完成」了
        when(detailDao.selectById(1L))
                .thenReturn(detail(1L, PurchaseServiceImpl.DETAIL_FINISHED, PURCHASE_ID));

        service.finish(PURCHASE_ID, List.of(1L), Collections.emptyList());

        verify(wareSkuService, never()).addStock(anyLong(), anyLong(), any(), any());
        // 也不该再写一次明细 —— 写了虽然值一样，但会刷掉 update_time，
        // 让「这条什么时候入的库」变得不可信。
        verify(detailDao, never()).updateById(any(PurchaseDetailEntity.class));
    }

    @Test
    @DisplayName("采购失败的明细允许改判成功并入库 —— 状态 4 时库存并没有加过")
    void failedDetailCanStillBeCompleted() {
        when(detailDao.selectById(1L))
                .thenReturn(detail(1L, PurchaseServiceImpl.DETAIL_FAILED, PURCHASE_ID));

        service.finish(PURCHASE_ID, List.of(1L), Collections.emptyList());

        // 只跳过状态 3 而不是「所有终态」，正是为了不堵死
        // 「先标失败、核实后改成功」这条正常路径。
        verify(wareSkuService, times(1)).addStock(eq(2001L), eq(1L), eq(10), any());
    }

    @Test
    @DisplayName("明细不属于这张采购单时跳过，绝不给它入库")
    void skipsDetailFromAnotherPurchase() {
        when(detailDao.selectById(9L))
                .thenReturn(detail(9L, PurchaseServiceImpl.DETAIL_RECEIVED, 999L));

        service.finish(PURCHASE_ID, List.of(9L), Collections.emptyList());

        verify(wareSkuService, never()).addStock(anyLong(), anyLong(), any(), any());
    }

    @Test
    @DisplayName("已完成入库的明细不能被改判成失败 —— 标失败不会把库存退回去")
    void refusesToMarkFinishedDetailAsFailed() {
        when(detailDao.selectById(1L))
                .thenReturn(detail(1L, PurchaseServiceImpl.DETAIL_FINISHED, PURCHASE_ID));

        service.finish(PURCHASE_ID, Collections.emptyList(), List.of(1L));

        // 允许改的话，结果是「明细显示采购失败，但仓库里凭空多了一批货」，
        // 对不上账而且不报错。
        verify(detailDao, never()).updateById(any(PurchaseDetailEntity.class));
    }

    /**
     * 反向对照：确认这些桩真的在起作用。
     *
     * 如果 selectById 没配对（比如 id 写错），mock 返回 null，
     * finish 里的 {@code if (d == null) continue;} 会让每一条测试都「因为什么都没发生」
     * 而通过 —— 包括那条「不能重复入库」的，给出虚假的安全感。
     * 这里用一条<b>必须发生入库</b>的路径把桩锁住。
     */
    @Test
    @DisplayName("反向对照：桩确实生效，未完成明细一定会走到入库")
    void stubsActuallyWire() {
        when(detailDao.selectById(1L))
                .thenReturn(detail(1L, PurchaseServiceImpl.DETAIL_ASSIGNED, PURCHASE_ID));

        service.finish(PURCHASE_ID, List.of(1L), Collections.emptyList());

        verify(detailDao).selectById(1L);
        verify(detailDao).updateById(any(PurchaseDetailEntity.class));
        verify(wareSkuService).addStock(anyLong(), anyLong(), any(), any());
    }
}

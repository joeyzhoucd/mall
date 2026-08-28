package com.mall.ware.service;

import com.mall.common.constant.StockLockStatus;
import com.mall.ware.dao.WareOrderTaskDetailDao;
import com.mall.ware.dao.WareSkuDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住 {@link StockAtomicOps} 选对了释放/扣减的 SQL。
 *
 * <h3>为什么这件事值得单独测</h3>
 * 走错分支<b>不会报任何错</b>：库存照样减了，sku 的总量也对，只是减错了仓库 ——
 * 每个仓的 {@code stock_locked} 会持续漂移。这种「总数对、分布错」的问题
 * 在单仓环境里永远不会暴露，等上了多仓才发现，而那时已经积累了一堆错账。
 * 所以它必须靠测试守，不能靠跑一遍看有没有异常。
 *
 * <h3>两条分支都要测，尤其是回退那条</h3>
 * {@code ware_id} 是后加的列，迁移之前创建的明细没有这个值。如果实现里
 * 直接要求 wareId 非空，那些还在流转中的历史订单会释放不掉库存 ——
 * 一次 schema 变更把在途数据卡死。所以「wareId 为 null 时仍然能释放」
 * 和「wareId 非空时走精确路径」是同等重要的两条契约。
 *
 * <h3>还顺带守住 CAS 的短路</h3>
 * CAS 没抢到处理权时<b>一行库存都不能动</b>。漏掉这个 return 会导致同一条明细
 * 被释放两次，库存凭空增加 —— 而这同样不会报错。
 */
class StockAtomicOpsTest {

    private WareSkuDao wareSkuDao;
    private WareOrderTaskDetailDao detailDao;
    private StockAtomicOps ops;

    @BeforeEach
    void setUp() {
        wareSkuDao = mock(WareSkuDao.class);
        detailDao = mock(WareOrderTaskDetailDao.class);
        ops = new StockAtomicOps();
        ReflectionTestUtils.setField(ops, "wareSkuDao", wareSkuDao);
        ReflectionTestUtils.setField(ops, "wareOrderTaskDetailDao", detailDao);
    }

    /** CAS 抢到处理权。 */
    private void casWins(int from, int to) {
        when(detailDao.casLockStatus(anyLong(), eq(from), eq(to))).thenReturn(1);
    }

    @Test
    @DisplayName("释放：有 wareId 时走按仓库的精确 SQL，绝不碰按 sku 那条")
    void unlockUsesWareSpecificSqlWhenWareIdPresent() {
        casWins(StockLockStatus.LOCKED, StockLockStatus.UNLOCKED);

        assertThat(ops.unlock(1L, 1001L, 7L, 3)).isTrue();

        verify(wareSkuDao).releaseLockedBySkuAndWare(1001L, 7L, 3);
        verify(wareSkuDao, never()).releaseLockedBySku(anyLong(), anyInt());
    }

    @Test
    @DisplayName("释放：wareId 为 null 时回退按 sku（迁移前的历史明细必须仍能释放）")
    void unlockFallsBackWhenWareIdMissing() {
        casWins(StockLockStatus.LOCKED, StockLockStatus.UNLOCKED);

        assertThat(ops.unlock(1L, 1001L, null, 3))
                .as("历史明细释放失败 —— 一次 schema 变更会把在途订单的库存卡死")
                .isTrue();

        verify(wareSkuDao).releaseLockedBySku(1001L, 3);
        verify(wareSkuDao, never()).releaseLockedBySkuAndWare(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("扣减：有 wareId 时走按仓库的精确 SQL")
    void deductUsesWareSpecificSqlWhenWareIdPresent() {
        casWins(StockLockStatus.LOCKED, StockLockStatus.DEDUCTED);

        assertThat(ops.deduct(1L, 1001L, 7L, 3)).isTrue();

        verify(wareSkuDao).deductStockBySkuAndWare(1001L, 7L, 3);
        verify(wareSkuDao, never()).deductStockBySku(anyLong(), anyInt());
    }

    @Test
    @DisplayName("扣减：wareId 为 null 时回退按 sku")
    void deductFallsBackWhenWareIdMissing() {
        casWins(StockLockStatus.LOCKED, StockLockStatus.DEDUCTED);

        assertThat(ops.deduct(1L, 1001L, null, 3)).isTrue();

        verify(wareSkuDao).deductStockBySku(1001L, 3);
        verify(wareSkuDao, never()).deductStockBySkuAndWare(anyLong(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("CAS 没抢到处理权时，一行库存都不能动")
    void doesNotTouchStockWhenCasLoses() {
        // 默认 mock 返回 0，即 CAS 失败
        assertThat(ops.unlock(1L, 1001L, 7L, 3)).isFalse();
        assertThat(ops.deduct(1L, 1001L, 7L, 3)).isFalse();

        verify(wareSkuDao, never()).releaseLockedBySkuAndWare(anyLong(), anyLong(), anyInt());
        verify(wareSkuDao, never()).releaseLockedBySku(anyLong(), anyInt());
        verify(wareSkuDao, never()).deductStockBySkuAndWare(anyLong(), anyLong(), anyInt());
        verify(wareSkuDao, never()).deductStockBySku(anyLong(), anyInt());
    }

    @Test
    @DisplayName("参数不合法时直接短路，不做 CAS 也不动库存")
    void rejectsInvalidArguments() {
        assertThat(ops.unlock(null, 1001L, 7L, 3)).isFalse();
        assertThat(ops.unlock(1L, null, 7L, 3)).isFalse();
        assertThat(ops.unlock(1L, 1001L, 7L, 0)).isFalse();
        assertThat(ops.unlock(1L, 1001L, 7L, null)).isFalse();

        verify(detailDao, never()).casLockStatus(anyLong(), anyInt(), anyInt());
    }
}

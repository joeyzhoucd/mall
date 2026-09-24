package com.mall.ware.service.impl;

import com.mall.ware.cache.WareHotCacheInvalidator;
import com.mall.ware.dao.WareSkuDao;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.vo.OrderItemLockVo;
import com.mall.ware.vo.WareSkuLockVo;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 锁库存的加锁顺序。
 *
 * <p>orderLockStock 在一个事务里逐个 SKU 执行 {@code UPDATE wms_ware_sku ... WHERE id = ?}，
 * 每把行锁持有到提交。订单行顺序来自购物车、是任意的，所以 A 先锁 X 再锁 Y、
 * B 先锁 Y 再锁 X 就会死锁 —— 十万单实测 mall-ware 8 小时 164 次，每次是一单真实的下单失败。
 * 必须按全局一致的顺序（sku_id 升序）加锁。
 */
class WareSkuLockOrderTest {

    @Test
    void locksSkusInAscendingSkuIdOrderRegardlessOfCartOrder() {
        WareSkuDao dao = mock(WareSkuDao.class);
        when(dao.lockStock(anyLong(), anyInt())).thenReturn(1);
        WareSkuServiceImpl service = spy(new WareSkuServiceImpl());
        ReflectionTestUtils.setField(service, "wareSkuDao", dao);
        ReflectionTestUtils.setField(service, "wareOrderTaskService", mock(WareOrderTaskService.class));
        ReflectionTestUtils.setField(service, "wareOrderTaskDetailService", mock(WareOrderTaskDetailService.class));
        ReflectionTestUtils.setField(service, "wareHotCacheInvalidator", mock(WareHotCacheInvalidator.class));
        // 每个 SKU 一个仓库行，ware_sku.id = sku_id * 10，方便从 lockStock 的参数反推 SKU
        doAnswer(inv -> {
            Long skuId = inv.getArgument(0);
            WareSkuEntity row = new WareSkuEntity();
            row.setId(skuId * 10);
            row.setSkuId(skuId);
            row.setWareId(1L);
            return List.of(row);
        }).when(service).listBySkuId(anyLong());

        WareSkuLockVo lock = new WareSkuLockVo();
        lock.setOrderSn("O1");
        List<OrderItemLockVo> items = new ArrayList<>();
        for (long skuId : new long[] {30L, 10L, 20L}) {   // 购物车顺序：乱序
            OrderItemLockVo item = new OrderItemLockVo();
            item.setSkuId(skuId);
            item.setCount(1);
            items.add(item);
        }
        lock.setLocks(items);

        assertThat(service.orderLockStock(lock)).isTrue();

        InOrder order = inOrder(dao);
        order.verify(dao).lockStock(100L, 1);
        order.verify(dao).lockStock(200L, 1);
        order.verify(dao).lockStock(300L, 1);
    }
}

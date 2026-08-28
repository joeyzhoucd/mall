package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.SeckillSkuRelationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 守住「秒杀库存不能从后台的 CRUD 接口直接改」。
 *
 * <h3>为什么这条比一般的字段白名单更要紧</h3>
 * 秒杀的真实库存<b>不在数据库里</b>，在 Redis 的 {@code seckill:stock:{id}} 上，
 * 抢购靠 Lua 原子扣减那个键。数据库里的 {@code seckill_count} 只是配置值，
 * 要通过 {@code activate} 才会被拷贝进去。
 * <p>
 * 所以改数据库有两种失败方式，都不报错：
 * 改了不 activate → 后台数字和实际能抢的数量对不上；
 * 改了去 activate → activate 会清空「谁已抢过」的集合，活动进行中做这件事
 * 等于让已中签的人再抢一次，<b>直接超卖</b>。
 * <p>
 * 超卖是这类系统最不能出的问题，而触发它只需要有人在后台点一下"修改"。
 */
class SeckillRelationWriteGuardTest {

    private SeckillSkuRelationController controller;
    private SeckillSkuRelationService service;

    private void setUp() {
        service = mock(SeckillSkuRelationService.class);
        controller = new SeckillSkuRelationController();
        ReflectionTestUtils.setField(controller, "seckillSkuRelationService", service);
    }

    @Test
    @DisplayName("带 seckillCount 或 soldCount 时必须拒绝，且一行都不能写")
    void rejectsStockFields() {
        setUp();

        SeckillSkuRelationEntity withCount = new SeckillSkuRelationEntity();
        withCount.setId(1L);
        withCount.setSeckillCount(new BigDecimal("500"));

        SeckillSkuRelationEntity withSold = new SeckillSkuRelationEntity();
        withSold.setId(1L);
        withSold.setSoldCount(0);

        for (SeckillSkuRelationEntity bad : java.util.List.of(withCount, withSold)) {
            R r = controller.update(bad);
            assertThat(r.get("code"))
                    .as("改秒杀库存必须走 activate 那条路径，直接改库会导致超卖或数字对不上")
                    .isNotEqualTo(0);
        }
        verify(service, never()).updateById(any());
    }

    @Test
    @DisplayName("只改价格/限购/排序时放行，且不携带库存字段")
    void allowsConfigFields() {
        setUp();

        SeckillSkuRelationEntity e = new SeckillSkuRelationEntity();
        e.setId(9L);
        e.setSeckillPrice(new BigDecimal("9.90"));
        e.setSeckillLimit(new BigDecimal("2"));
        e.setSeckillSort(3);
        // 白名单之外
        e.setSkuId(123L);

        assertThat(controller.update(e).get("code")).isEqualTo(0);

        ArgumentCaptor<SeckillSkuRelationEntity> captor =
                ArgumentCaptor.forClass(SeckillSkuRelationEntity.class);
        verify(service).updateById(captor.capture());
        SeckillSkuRelationEntity w = captor.getValue();
        assertThat(w.getSeckillPrice()).isEqualByComparingTo("9.90");
        assertThat(w.getSeckillCount()).as("库存字段绝不能被带进写入对象").isNull();
        assertThat(w.getSoldCount()).isNull();
        assertThat(w.getSkuId()).as("skuId 不在白名单，不该被写").isNull();
    }
}

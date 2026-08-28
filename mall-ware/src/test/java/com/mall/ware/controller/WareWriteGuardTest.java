package com.mall.ware.controller;

import com.mall.common.utils.R;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.service.WareSkuService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 守住「并发关键字段不能从脚手架接口直接写」。
 *
 * <h3>为什么这件事必须有测试守着</h3>
 * 这些 {@code /update} 是 renren 生成器生成的，形态是「整个实体丢给 updateById」。
 * 那个写法看起来完全正常，很容易被当成标准 CRUD 再写回来 —— 尤其是以后再用生成器
 * 生成新模块的时候。而它一旦回来，破坏是<b>静默</b>的：
 * {@code stock_locked} 是订单流程用 CAS 维护的账，被裸写之后可售量立刻失真，
 * 但没有任何报错，要等到某笔订单发不出货才发现，那时已经无从追溯。
 *
 * <h3>为什么用直接调方法而不是 MockMvc</h3>
 * 这里要验证的是<b>方法自己的校验逻辑</b>，跟 HTTP 层、序列化、路由都无关。
 * 起一个 web 上下文只会让这条测试变慢、并且引入一堆和被测逻辑无关的失败可能。
 */
class WareWriteGuardTest {

    @Test
    @DisplayName("waresku/update：带 stock 或 stockLocked 时必须拒绝，且一行都不能写")
    void wareSkuUpdateRejectsQuantityFields() {
        WareSkuService service = mock(WareSkuService.class);
        WareSkuController controller = new WareSkuController();
        ReflectionTestUtils.setField(controller, "wareSkuService", service);

        WareSkuEntity withStock = new WareSkuEntity();
        withStock.setId(1L);
        withStock.setStock(999);

        WareSkuEntity withLocked = new WareSkuEntity();
        withLocked.setId(1L);
        withLocked.setStockLocked(0);

        for (WareSkuEntity bad : Arrays.asList(withStock, withLocked)) {
            R r = controller.update(bad);
            assertThat(r.get("code"))
                    .as("带数量字段的请求必须被拒绝，否则会绕过 CAS 直接改库存账")
                    .isNotEqualTo(0);
        }
        verify(service, never()).updateById(any());
    }

    @Test
    @DisplayName("waresku/update：只改名称时放行，且只写白名单字段")
    void wareSkuUpdateAllowsDescriptiveFields() {
        WareSkuService service = mock(WareSkuService.class);
        WareSkuController controller = new WareSkuController();
        ReflectionTestUtils.setField(controller, "wareSkuService", service);

        WareSkuEntity e = new WareSkuEntity();
        e.setId(7L);
        e.setSkuName("新名字");
        // 白名单之外的字段：即使传了也不该被写进去
        e.setWareId(999L);
        e.setSkuId(888L);

        assertThat(controller.update(e).get("code")).isEqualTo(0);

        org.mockito.ArgumentCaptor<WareSkuEntity> captor =
                org.mockito.ArgumentCaptor.forClass(WareSkuEntity.class);
        verify(service).updateById(captor.capture());
        WareSkuEntity written = captor.getValue();
        assertThat(written.getId()).isEqualTo(7L);
        assertThat(written.getSkuName()).isEqualTo("新名字");
        assertThat(written.getWareId()).as("wareId 不在白名单，不该被写").isNull();
        assertThat(written.getSkuId()).as("skuId 不在白名单，不该被写").isNull();
    }

    @Test
    @DisplayName("wareordertaskdetail 不应再暴露任何写接口（lock_status 是 CAS 状态机）")
    void taskDetailControllerHasNoWriteEndpoints() {
        // 用反射看方法，而不是看源码字符串：改了实现但忘了这条约束时，这里会失败。
        for (Method m : WareOrderTaskDetailController.class.getDeclaredMethods()) {
            String name = m.getName();
            assertThat(name)
                    .as("WareOrderTaskDetailController 不应有 %s —— lock_status 只能由 "
                            + "StockAtomicOps 的 CAS 迁移，裸写会让同一笔库存被释放两次", name)
                    .isNotIn("save", "update", "delete");
        }
    }
}

package com.mall.product.controller;

import com.mall.product.service.SkuInfoService;
import com.mall.product.vo.SkuItemVo;
import com.mall.product.entity.SkuInfoEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商品不存在时详情页要 404，不能 500。
 *
 * <h3>对应的真实行为（2026-09-14 修）</h3>
 * {@code /10.html}（库里没有这个 skuId）之前返回 <b>500</b>。
 * 原因是 {@code skuInfoService.item()} 对不存在的商品返回的不是 null，
 * 而是一个 {@code info} 为 null 的 VO；控制器照单全收塞进 model，
 * 模板里 {@code ${item.info.price}} 在<b>渲染阶段</b>抛 NPE。
 * <p>
 * 500 和 404 的区别不只是好看：500 意味着「服务端坏了」，
 * 会进错误率指标、会触发告警；而「这个商品没有」是一个完全正常的客户端请求结果。
 * 爬虫和分享出去的失效链接会源源不断地打这种请求。
 */
class ItemControllerNotFoundTest {

    private ItemController controllerReturning(SkuItemVo vo) {
        SkuInfoService service = mock(SkuInfoService.class);
        when(service.item(anyLong())).thenReturn(vo);
        ItemController controller = new ItemController();
        ReflectionTestUtils.setField(controller, "skuInfoService", service);
        return controller;
    }

    @Test
    @DisplayName("商品不存在（info 为 null）要抛 404，不能让模板去 NPE")
    void missingSkuYields404() {
        // item() 对不存在的商品返回的是「壳子」：VO 有，info 是 null
        ItemController controller = controllerReturning(new SkuItemVo());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.skuItem(10L, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode(),
                "商品不存在应该是 404；500 会把正常的失效链接算成服务端故障");
    }

    @Test
    @DisplayName("service 直接返回 null 时同样是 404")
    void nullVoYields404() {
        ItemController controller = controllerReturning(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.skuItem(10L, new ConcurrentModel()));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    /**
     * 负控：商品存在时必须正常返回视图名并把 item 放进 model。
     * 没有这条的话，「一律 404」也能让上面两条通过。
     */
    @Test
    @DisplayName("负控：商品存在时要正常渲染 item 视图")
    void existingSkuRendersItemView() {
        SkuItemVo vo = new SkuItemVo();
        SkuInfoEntity info = new SkuInfoEntity();
        info.setSkuId(1001L);
        vo.setInfo(info);

        ItemController controller = controllerReturning(vo);
        Model model = new ConcurrentModel();

        String view = controller.skuItem(1001L, model);

        assertEquals("item", view);
        assertNotNull(model.getAttribute("item"), "模板要用的 item 没放进 model");
    }
}

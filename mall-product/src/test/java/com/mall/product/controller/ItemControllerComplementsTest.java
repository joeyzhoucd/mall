package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.feign.OrderRecoFeignService;
import com.mall.product.feign.SearchFeignService;
import com.mall.product.feign.SearchHydrateFeignService;
import com.mall.product.service.SkuInfoService;
import com.mall.product.vo.SimilarItemVo;
import com.mall.product.vo.SkuItemVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 详情页「搭配购买」：顺序、截断、每一处失败都只让这一块消失、开关、并行 */
class ItemControllerComplementsTest {

    private static final long SKU = 10L, SPU = 77L;

    private SearchFeignService search;
    private OrderRecoFeignService orderReco;
    private SearchHydrateFeignService hydrate;
    private ItemController controller;
    private final AtomicInteger submitted = new AtomicInteger();

    @BeforeEach
    void setUp() {
        SkuInfoService skuInfo = mock(SkuInfoService.class);
        SkuItemVo vo = new SkuItemVo();
        SkuInfoEntity info = new SkuInfoEntity();
        info.setSkuId(SKU);
        info.setSpuId(SPU);
        vo.setInfo(info);
        when(skuInfo.item(SKU)).thenReturn(vo);
        search = mock(SearchFeignService.class);
        orderReco = mock(OrderRecoFeignService.class);
        hydrate = mock(SearchHydrateFeignService.class);
        when(search.similar(anyLong(), anyInt())).thenReturn(R.ok().put("items", List.of(card(1))));

        controller = new ItemController();
        ReflectionTestUtils.setField(controller, "skuInfoService", skuInfo);
        ReflectionTestUtils.setField(controller, "searchFeignService", search);
        ReflectionTestUtils.setField(controller, "orderRecoFeignService", orderReco);
        ReflectionTestUtils.setField(controller, "searchHydrateFeignService", hydrate);
        ReflectionTestUtils.setField(controller, "objectMapper", new ObjectMapper());
        // 真的异步执行，同时数一下提交了几个任务
        ReflectionTestUtils.setField(controller, "applicationTaskExecutor", new SimpleAsyncTaskExecutor() {
            @Override
            public void execute(Runnable task) {
                submitted.incrementAndGet();
                super.execute(task);
            }
        });
    }

    private static Map<String, Object> card(long sku) {
        return Map.of("skuId", sku, "skuTitle", "t" + sku, "skuPrice", 1, "skuImg", "i");
    }

    @SuppressWarnings("unchecked")
    private List<SimilarItemVo> render() {
        ConcurrentModel model = new ConcurrentModel();
        assertThat(controller.skuItem(SKU, model)).isEqualTo("item");
        assertThat(model.getAttribute("similar")).as("相似商品不受搭配购买影响").asList().hasSize(1);
        return (List<SimilarItemVo>) model.getAttribute("complements");
    }

    @Test
    void complementsKeepRecommendationOrderAndAreTruncatedToEight() {
        List<Map<String, Object>> ids = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            ids.add(Map.of("spuId", 1000 + i, "score", 100 - i));
        }
        when(orderReco.complements(eq(SPU), eq(16))).thenReturn(R.ok().put("items", ids));
        List<Map<String, Object>> cards = new ArrayList<>();
        for (int i = 0; i < 12; i++) {           // 16 个里只有 12 个有货
            cards.add(card(500 + i));
        }
        when(hydrate.bySpus(anyList())).thenReturn(R.ok().put("items", cards));

        List<SimilarItemVo> got = render();
        assertThat(got).extracting(SimilarItemVo::getSkuId).containsExactly(500L, 501L, 502L, 503L, 504L, 505L, 506L, 507L);
        verify(hydrate).bySpus(List.of(1000L, 1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L,
                1008L, 1009L, 1010L, 1011L, 1012L, 1013L, 1014L, 1015L));
    }

    @Test
    void orderServiceFailureOnlyRemovesThisBlock() {
        when(orderReco.complements(anyLong(), anyInt())).thenThrow(new RuntimeException("mall-order 不可用"));
        assertThat(render()).isEmpty();
        verify(hydrate, never()).bySpus(anyList());
    }

    @Test
    void searchHydrationFailureOnlyRemovesThisBlock() {
        when(orderReco.complements(anyLong(), anyInt())).thenReturn(R.ok().put("items", List.of(Map.of("spuId", 1, "score", 1))));
        when(hydrate.bySpus(anyList())).thenThrow(new RuntimeException("mall-search 超时"));
        assertThat(render()).isEmpty();
    }

    @Test
    void noRecommendationsSkipsSearch() {
        when(orderReco.complements(anyLong(), anyInt())).thenReturn(R.ok().put("items", List.of()));
        assertThat(render()).isEmpty();
        verifyNoInteractions(hydrate);
    }

    @Test
    void killSwitchSkipsBothCalls() {
        ReflectionTestUtils.setField(controller, "complementsEnabled", false);
        assertThat(render()).isEmpty();
        verifyNoInteractions(orderReco, hydrate);
    }

    @Test
    void bothBlocksAreSubmittedToTheExecutor() {
        when(orderReco.complements(anyLong(), anyInt())).thenReturn(R.ok().put("items", List.of()));
        render();
        assertThat(submitted.get()).as("相似商品 + 搭配购买 各一个异步任务").isEqualTo(2);
    }
}

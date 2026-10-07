package com.mall.product.controller;

import com.mall.common.annotation.PublicApi;

import com.mall.common.constant.ResponseKeys;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.product.feign.OrderRecoFeignService;
import com.mall.product.feign.SearchFeignService;
import com.mall.product.feign.SearchHydrateFeignService;
import com.mall.product.service.SkuInfoService;
import com.mall.product.vo.ComplementIdVo;
import com.mall.product.vo.SimilarItemVo;
import com.mall.product.vo.SkuItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 商品详情页（PDP）。
 *
 * <h3>这里的异常处理为什么要在进模板之前做完</h3>
 * 这个方法返回的是<b>视图名</b>，渲染发生在它 return 之后。
 * 也就是说模板里抛的异常是在 {@code DispatcherServlet} 把响应交给
 * {@code ThymeleafView.render()} 之后才出现的，
 * 那时候 {@code @ExceptionHandler} / {@code @ControllerAdvice} 的作用域
 * <b>已经结束了</b> —— 全局兜底捕获不到，日志里只看得见 /error 上的二次异常。
 * <p>
 * 2026-09-14 的详情页 500 就是这么来的（模板里迭代到一个 null 元素），
 * 排查时看日志完全看不出真实原因。
 * <p>
 * 结论：凡是模板会直接解引用的东西，都必须在<b>这里</b>就保证成立，
 * 不能指望后面有人接住。
 */
@Controller
public class ItemController {

    private static final Logger log = LoggerFactory.getLogger(ItemController.class);

    /** 详情页推荐位放几条。8 条正好铺满一行两屏，再多页面就太长了。 */
    private static final int SIMILAR_SIZE = 8;

    @Autowired
    private SkuInfoService skuInfoService;

    @Autowired
    private SearchFeignService searchFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    /** 「搭配购买」放几条，和相似商品一样铺满一行 */
    private static final int COMPLEMENT_SIZE = 8;
    /** 向 mall-order 多要一倍：mall-search 只回有货的，没货的会缺席 */
    private static final int COMPLEMENT_FETCH = 16;

    @Autowired
    private OrderRecoFeignService orderRecoFeignService;

    @Autowired
    private SearchHydrateFeignService searchHydrateFeignService;

    /** 虚拟线程的 SimpleAsyncTaskExecutor（同 SkuInfoServiceImpl 的说明） */
    @Autowired
    private AsyncTaskExecutor applicationTaskExecutor;

    /**
     * 运维总开关：整块关掉。<b>故障演练不用它</b> —— 关掉等于「不调用」，
     * 测不到「调用失败」时的降级；演练是把 orderReco 的 Feign url 指到一个不可达地址。
     */
    @Value("${mall.product.item.complements.enabled:true}")
    private boolean complementsEnabled = true;

    @GetMapping("/{skuId}.html")
    @PublicApi
    public String skuItem(@PathVariable("skuId") Long skuId, Model model) {
        SkuItemVo vo = skuInfoService.item(skuId);

        // 商品不存在时 item() 返回的是一个 info 为 null 的 VO（不是 null 本身）。
        // 不拦的话模板里 ${item.info.price} 会在渲染阶段抛 NPE，
        // 表现成 500 —— 而"这个商品没有"本来就该是 404。
        // 实测：/10.html（库里没有的 skuId）改之前就是 500。
        if (vo == null || vo.getInfo() == null) {
            log.debug("商品不存在，skuId={}", skuId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        model.addAttribute("item", vo);
        // 两块推荐并行：它们互不依赖，串行的话详情页要多等一整个推荐链路。
        // 两个 load 方法都自己兜住异常、永远返回列表，所以 join 不会抛。
        Long spuId = vo.getInfo().getSpuId();
        CompletableFuture<List<SimilarItemVo>> similar =
                CompletableFuture.supplyAsync(() -> loadSimilar(skuId), applicationTaskExecutor);
        CompletableFuture<List<SimilarItemVo>> complements =
                CompletableFuture.supplyAsync(() -> loadComplements(spuId), applicationTaskExecutor);
        model.addAttribute("similar", similar.join());
        model.addAttribute("complements", complements.join());
        return "item";
    }

    /**
     * 拉「搭配购买」：mall-order 给 SPU 列表（LLR，已排除同类目）→ mall-search 补成有货 SKU。
     * 和 {@link #loadSimilar} 同一个约束：失败在这里变成空列表，模板里整块不出现。
     */
    private List<SimilarItemVo> loadComplements(Long spuId) {
        if (!complementsEnabled || spuId == null) {
            return List.of();
        }
        try {
            List<ComplementIdVo> ids = RUtils.getData(orderRecoFeignService.complements(spuId, COMPLEMENT_FETCH),
                    ResponseKeys.ITEMS, objectMapper, new TypeReference<List<ComplementIdVo>>() {});
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            List<SimilarItemVo> items = RUtils.getData(
                    searchHydrateFeignService.bySpus(ids.stream().map(ComplementIdVo::getSpuId).toList()),
                    ResponseKeys.ITEMS, objectMapper, new TypeReference<List<SimilarItemVo>>() {});
            return items == null ? List.of() : items.stream().limit(COMPLEMENT_SIZE).toList();
        } catch (Exception e) {
            log.warn("搭配购买拉取失败，推荐位留空，spuId={}", spuId, e);
            return List.of();
        }
    }

    /**
     * 拉相似商品推荐。
     *
     * <p><b>这里的 catch 不是防御性编程，是这个类头上那条约束的直接后果</b>：
     * 模板会迭代 {@code ${similar}}，而渲染发生在 return 之后 ——
     * 那时候异常没人接得住，表现是详情页 500。
     * 所以推荐位的失败必须在<b>这里</b>就变成"空列表"，
     * 让页面少一块内容，而不是整页打不开。
     *
     * <p>mall-search 那边的 service 已经做了三级降级（向量 → 同类目热度 → 空），
     * 这里再兜一层是因为<b>那些降级都在对端进程里</b>：
     * mall-search 整个不可用、Feign 超时、返回体格式变了，对端的降级一个都跑不到。
     */
    private List<SimilarItemVo> loadSimilar(Long skuId) {
        try {
            R r = searchFeignService.similar(skuId, SIMILAR_SIZE);
            List<SimilarItemVo> list = RUtils.getData(r, ResponseKeys.ITEMS, objectMapper,
                    new TypeReference<List<SimilarItemVo>>() {});
            return list == null ? List.of() : list;
        } catch (Exception e) {
            // 只记 warn 不记 error：推荐位没了不是故障，详情页还是好的
            log.warn("相似商品拉取失败，推荐位留空，skuId={}", skuId, e);
            return List.of();
        }
    }
}

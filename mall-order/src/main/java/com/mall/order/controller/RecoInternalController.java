package com.mall.order.controller;

import com.mall.common.constant.ResponseKeys;
import com.mall.common.utils.R;
import com.mall.order.reco.ComplementQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 给 mall-product 详情页用的「搭配购买」内部接口，只返回 SPU id 和分数 ——
 * 标题、价格、图片、有没有货由 mall-search 按 SPU 补全（那些数据不归 mall-order）。
 *
 * <p><b>路径里的 /internal/ 是给网关看的</b>：网关的 order_route 是 Path=/order/**，
 * 不拦的话这个接口公网可达（已知缺陷第 5 条那一类）。网关的 InternalPathGuardFilter
 * 对 /order/internal/**（以及后台路由改写前的 /api/order/internal/**）一律 404。
 */
@RestController
@RequestMapping("/order/internal/reco")
public class RecoInternalController {

    private final ComplementQueryService complementQueryService;

    public RecoInternalController(ComplementQueryService complementQueryService) {
        this.complementQueryService = complementQueryService;
    }

    @GetMapping("/complements/{spuId}")
    public R complements(@PathVariable("spuId") Long spuId,
                         @RequestParam(value = "size", defaultValue = "8") Integer size) {
        return R.ok().put(ResponseKeys.ITEMS, complementQueryService.complements(spuId, size == null ? 8 : size));
    }
}

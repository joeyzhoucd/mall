package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient("mall-search")
public interface SearchFeignService {

    /**
     * 上架商品到Elasticsearch
     */
    @PostMapping("/search/product/up")
    R productUp(@RequestBody List<Object> skuEsModels);

    /**
     * 从 Elasticsearch 删除这些 sku 的文档（下架 / 删除商品时调用）。
     */
    @PostMapping("/search/product/down")
    R productDown(@RequestBody List<Long> skuIds);

    /**
     * 相似商品推荐，详情页右侧/下方的推荐位用。
     *
     * <p>调用方<b>必须自己兜住异常</b>：mall-search 不可用时这里会抛，
     * 而详情页不能因为推荐位挂掉就 500。
     */
    @GetMapping("/search/similar")
    R similar(@RequestParam("skuId") Long skuId, @RequestParam("size") Integer size);

}


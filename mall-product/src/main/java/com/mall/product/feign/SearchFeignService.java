package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

}


package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.service.SkuInfoService;
import com.mall.product.service.SkuSaleAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CartFeignController {

    @Autowired
    private SkuInfoService skuInfoService;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @GetMapping("/product/skuinfo/info/{skuId}")
    public R skuInfo(@PathVariable("skuId") Long skuId) {
        SkuInfoEntity info = skuInfoService.getById(skuId);
        return R.ok().put("skuInfo", info);
    }

    @GetMapping("/product/skusaleattrvalue/values/{skuId}")
    public List<String> skuSaleAttrValues(@PathVariable("skuId") Long skuId) {
        return skuSaleAttrValueService.getSkuSaleAttrValuesAsStringList(skuId);
    }
}


package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SkuSaleAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/skusaleattrvalue")
public class SkuSaleAttrValueController {
    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKU销售属性占位符方法");
    }
}
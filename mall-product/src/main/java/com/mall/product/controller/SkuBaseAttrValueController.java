package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SkuBaseAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/skubaseattrvalue")
public class SkuBaseAttrValueController {
    @Autowired
    private SkuBaseAttrValueService skuBaseAttrValueService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKU基本属性占位符方法");
    }
}
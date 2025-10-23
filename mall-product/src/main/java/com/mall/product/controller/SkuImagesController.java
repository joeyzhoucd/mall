package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SkuImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/skuimages")
public class SkuImagesController {
    @Autowired
    private SkuImagesService skuImagesService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKU图片占位符方法");
    }
}
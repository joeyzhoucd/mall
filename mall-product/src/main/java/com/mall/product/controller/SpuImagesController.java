package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SpuImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/spuimages")
public class SpuImagesController {
    @Autowired
    private SpuImagesService spuImagesService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU图片占位符方法");
    }
}
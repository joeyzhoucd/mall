package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SpuInfoDescService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/spuinfodesc")
public class SpuInfoDescController {
    @Autowired
    private SpuInfoDescService spuInfoDescService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU信息描述占位符方法");
    }
}
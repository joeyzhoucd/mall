package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.ProductAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/productattrvalue")
public class ProductAttrValueController {
    @Autowired
    private ProductAttrValueService productAttrValueService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU attributes placeholder method");
    }
}
package com.joeyzhoucd.product.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.service.SkuBaseAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * sku基本属性&值
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/skubaseattrvalue")
public class SkuBaseAttrValueController {
    @Autowired
    private SkuBaseAttrValueService skuBaseAttrValueService;

    /**
     * 预留接口 - SKU基本属性值功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKU基本属性值功能待开发");
    }
}

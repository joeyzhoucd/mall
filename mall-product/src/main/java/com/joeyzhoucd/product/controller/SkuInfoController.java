package com.joeyzhoucd.product.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.service.SkuInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * sku信息
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/skuinfo")
public class SkuInfoController {
    @Autowired
    private SkuInfoService skuInfoService;

    /**
     * 预留接口 - SKU信息功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKU信息功能待开发");
    }
}

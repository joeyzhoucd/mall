package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SkuSaleAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * skué”€å”®å±žæ€§&å€¼
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/skusaleattrvalue")
public class SkuSaleAttrValueController {
    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    /**
     * é¢„ç•™æŽ¥å£ - SKUé”€å”®å±žæ€§å€¼åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKUé”€å”®å±žæ€§å€¼åŠŸèƒ½å¾…å¼€å‘");
    }
}

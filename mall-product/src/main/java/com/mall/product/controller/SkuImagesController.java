package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SkuImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * skuå›¾ç‰‡
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/skuimages")
public class SkuImagesController {
    @Autowired
    private SkuImagesService skuImagesService;

    /**
     * é¢„ç•™æŽ¥å£ - SKUå›¾ç‰‡åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SKUå›¾ç‰‡åŠŸèƒ½å¾…å¼€å‘");
    }
}

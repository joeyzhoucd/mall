package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SpuImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * spuå›¾ç‰‡
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/spuimages")
public class SpuImagesController {
    @Autowired
    private SpuImagesService spuImagesService;

    /**
     * é¢„ç•™æŽ¥å£ - SPUå›¾ç‰‡åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPUå›¾ç‰‡åŠŸèƒ½å¾…å¼€å‘");
    }
}

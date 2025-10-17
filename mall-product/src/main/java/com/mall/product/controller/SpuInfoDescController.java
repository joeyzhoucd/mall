package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SpuInfoDescService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * spuä¿¡æ¯ä»‹ç»
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/spuinfodesc")
public class SpuInfoDescController {
    @Autowired
    private SpuInfoDescService spuInfoDescService;

    /**
     * é¢„ç•™æŽ¥å£ - SPUä¿¡æ¯ä»‹ç»åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPUä¿¡æ¯ä»‹ç»åŠŸèƒ½å¾…å¼€å‘");
    }
}

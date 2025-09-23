package com.joeyzhoucd.product.controller;

import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.service.SpuCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品评价
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/spucomment")
public class SpuCommentController {
    @Autowired
    private SpuCommentService spuCommentService;

    /**
     * 预留接口 - 商品评价功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "商品评价功能待开发");
    }
}

package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.SpuCommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/spucomment")
public class SpuCommentController {
    @Autowired
    private SpuCommentService spuCommentService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU评论占位符方法");
    }
}
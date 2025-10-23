package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.CommentReplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("product/commentreplay")
public class CommentReplayController {
    @Autowired
    private CommentReplayService commentReplayService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "这是一个占位符方法，用于测试评论回复功能");
    }
}
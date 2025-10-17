package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.service.CommentReplayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * å•†å“è¯„ä»·å›žå¤å…³ç³»
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/commentreplay")
public class CommentReplayController {
    @Autowired
    private CommentReplayService commentReplayService;

    /**
     * é¢„ç•™æŽ¥å£ - å•†å“è¯„ä»·å›žå¤åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "å•†å“è¯„ä»·å›žå¤åŠŸèƒ½å¾…å¼€å‘");
    }
}

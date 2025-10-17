package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * è®¢å•æŽ§åˆ¶å™¨
 * ä¿ç•™åŸºç¡€ç»“æž„ï¼Œåˆ é™¤å‰ç«¯æœªä½¿ç”¨çš„æ–¹æ³•
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 22:49:21
 */
@RestController
@RequestMapping("order/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * é¢„ç•™æŽ¥å£ - è®¢å•åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "è®¢å•åŠŸèƒ½å¾…å¼€å‘");
    }
}

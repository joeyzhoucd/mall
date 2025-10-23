package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("order/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "This is a placeholder method");
    }
}
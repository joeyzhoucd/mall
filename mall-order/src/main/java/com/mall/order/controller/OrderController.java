package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderEntity;
import com.mall.order.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("order/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @GetMapping("/status/{orderSn}")
    public R getOrderStatus(@PathVariable("orderSn") String orderSn) {
        OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order == null) {
            return R.error(ErrorCode.ORDER_NOT_FOUND);
        }
        return R.ok().put("order", order).put("status", order.getStatus());
    }

    @PostMapping("/operate")
    public R recordOperate(@RequestBody com.mall.common.to.OrderOperateTo operateTo) {
        orderService.recordOperateHistory(operateTo);
        return R.ok();
    }
}
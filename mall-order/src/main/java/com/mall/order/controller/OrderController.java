package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.OrderStatus;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderEntity;
import com.mall.order.service.OrderService;
import org.apache.commons.lang3.StringUtils;
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
        return R.ok()
                .put("order", order)
                .put("status", order.getStatus())
                .put("statusName", OrderStatus.valueOfCode(order.getStatus()))
                .put("allowedTargets", OrderStatus.allowedTargets(order.getStatus()));
    }

    @GetMapping("/statuses")
    public R getOrderStatuses() {
        return R.ok()
                .put("statuses", OrderStatus.definitions())
                .put("transitionTable", OrderStatus.transitionTable())
                .put("transitions", OrderStatus.transitions());
    }

    @PostMapping("/ship")
    public R shipOrder(@RequestBody ShipOrderRequest request) {
        if (request == null || StringUtils.isAnyBlank(request.orderSn(), request.deliveryCompany(), request.deliverySn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.shipOrder(request.orderSn(), request.deliveryCompany(), request.deliverySn()));
    }

    @PostMapping("/receive")
    public R receiveOrder(@RequestBody OrderSnRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.receiveOrder(request.orderSn()));
    }

    @PostMapping("/complete")
    public R completeOrder(@RequestBody OrderSnRequest request) {
        return receiveOrder(request);
    }

    @PostMapping("/after-sale/start")
    public R startAfterSale(@RequestBody AfterSaleRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.startAfterSale(request.orderSn(), request.note()));
    }

    @PostMapping("/after-sale/finish")
    public R finishAfterSale(@RequestBody AfterSaleRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.finishAfterSale(request.orderSn(), request.note()));
    }

    @PostMapping("/operate")
    public R recordOperate(@RequestBody com.mall.common.to.OrderOperateTo operateTo) {
        orderService.recordOperateHistory(operateTo);
        return R.ok();
    }

    private R transitionResult(boolean updated) {
        return updated ? R.ok() : R.error(ErrorCode.ORDER_STATUS_TRANSITION_ILLEGAL);
    }

    public record ShipOrderRequest(String orderSn, String deliveryCompany, String deliverySn) {
    }

    public record OrderSnRequest(String orderSn) {
    }

    public record AfterSaleRequest(String orderSn, String note) {
    }
}

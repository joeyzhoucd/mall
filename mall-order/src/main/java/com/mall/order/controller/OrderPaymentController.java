package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.service.OrderPaymentService;
import com.mall.order.service.PaymentReconciliationService;
import com.mall.order.vo.pay.CreateOrderPaymentRequest;
import com.mall.order.vo.pay.PaymentNotifyRequest;
import com.mall.order.vo.pay.RefundOrderPaymentRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/order/payments")
public class OrderPaymentController {

    private final OrderPaymentService orderPaymentService;
    private final PaymentReconciliationService paymentReconciliationService;

    public OrderPaymentController(OrderPaymentService orderPaymentService,
                                  PaymentReconciliationService paymentReconciliationService) {
        this.orderPaymentService = orderPaymentService;
        this.paymentReconciliationService = paymentReconciliationService;
    }

    @PostMapping("/{orderSn}")
    public R createPayment(@PathVariable String orderSn, @RequestBody CreateOrderPaymentRequest request) {
        return R.ok().put("payment", orderPaymentService.createPayment(orderSn, request));
    }

    @GetMapping("/{channel}/{orderSn}")
    public R queryPayment(@PathVariable String channel, @PathVariable String orderSn) {
        return R.ok().put("payment", orderPaymentService.queryPayment(channel, orderSn));
    }

    @PostMapping("/notify")
    public R notify(@RequestBody PaymentNotifyRequest request) {
        return R.ok().put("result", orderPaymentService.handleNotify(request));
    }

    @PostMapping("/refunds")
    public R refund(@RequestBody RefundOrderPaymentRequest request) {
        return R.ok().put("refund", orderPaymentService.refund(request));
    }

    @PostMapping("/reconciliation/{date}")
    public R reconcile(@PathVariable LocalDate date) {
        return R.ok().put("summary", paymentReconciliationService.reconcile(date));
    }
}

package com.mall.payment.controller;

import com.mall.common.utils.R;
import com.mall.payment.model.PaymentChannel;
import com.mall.payment.model.PaymentRequest;
import com.mall.payment.model.PaymentResponse;
import com.mall.payment.model.PaymentStatus;
import com.mall.payment.model.RefundRequest;
import com.mall.payment.model.RefundResponse;
import com.mall.payment.service.PaymentMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/payment/mock")
public class PaymentMockController {

    private final PaymentMockService paymentMockService;

    public PaymentMockController(PaymentMockService paymentMockService) {
        this.paymentMockService = paymentMockService;
    }

    @PostMapping("/payments")
    public R createPayment(@RequestBody PaymentRequest request) {
        PaymentResponse response = paymentMockService.createPayment(request);
        return R.ok().put("payment", response);
    }

    @GetMapping("/payments/{channel}/{orderSn}")
    public R queryPayment(@PathVariable String channel, @PathVariable String orderSn) {
        PaymentResponse response = paymentMockService.queryPayment(PaymentChannel.from(channel), orderSn);
        return R.ok().put("payment", response);
    }

    @PostMapping("/payments/{channel}/{orderSn}/success")
    public R simulateSuccess(@PathVariable String channel,
                             @PathVariable String orderSn,
                             @RequestParam(value = "notify", defaultValue = "true") boolean notify) {
        PaymentResponse response = paymentMockService.transition(PaymentChannel.from(channel), orderSn, PaymentStatus.SUCCESS);
        R result = R.ok().put("payment", response);
        if (notify) {
            result.put("notify", paymentMockService.buildNotifyPayload(response));
        }
        return result;
    }

    @PostMapping("/payments/{channel}/{orderSn}/close")
    public R simulateClose(@PathVariable String channel,
                           @PathVariable String orderSn,
                           @RequestParam(value = "notify", defaultValue = "true") boolean notify) {
        PaymentResponse response = paymentMockService.transition(PaymentChannel.from(channel), orderSn, PaymentStatus.CLOSED);
        R result = R.ok().put("payment", response);
        if (notify) {
            result.put("notify", paymentMockService.buildNotifyPayload(response));
        }
        return result;
    }

    @PostMapping("/refunds")
    public R refund(@RequestBody RefundRequest request) {
        RefundResponse response = paymentMockService.refund(request);
        return R.ok().put("refund", response);
    }

    @GetMapping(value = "/reconciliation", produces = "text/csv;charset=UTF-8")
    public String reconciliation(@RequestParam("date") LocalDate date) {
        return paymentMockService.exportReconciliationCsv(date);
    }
}

package com.mall.payment.controller;

import com.mall.payment.model.PaymentChannel;
import com.mall.payment.model.PaymentRequest;
import com.mall.payment.model.PaymentResponse;
import com.mall.payment.model.RefundRequest;
import com.mall.payment.model.RefundResponse;
import com.mall.payment.service.PaymentMockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/payment/provider-mock")
public class PaymentProviderMockController {

    private final PaymentMockService paymentMockService;

    public PaymentProviderMockController(PaymentMockService paymentMockService) {
        this.paymentMockService = paymentMockService;
    }

    @PostMapping("/alipay/trade/precreate")
    public Map<String, Object> alipayPrecreate(@RequestBody PaymentRequest request) {
        return paymentMockService.createPayment(request.withChannel(PaymentChannel.ALIPAY)).providerPayload();
    }

    @GetMapping("/alipay/trade/query/{orderSn}")
    public Map<String, Object> alipayQuery(@PathVariable String orderSn) {
        return paymentMockService.queryPayment(PaymentChannel.ALIPAY, orderSn).providerPayload();
    }

    @PostMapping("/wechat/pay/transactions/native")
    public Map<String, Object> wechatNativePay(@RequestBody PaymentRequest request) {
        return paymentMockService.createPayment(request.withChannel(PaymentChannel.WECHAT)).providerPayload();
    }

    @GetMapping("/wechat/pay/transactions/out-trade-no/{orderSn}")
    public Map<String, Object> wechatQuery(@PathVariable String orderSn) {
        return paymentMockService.queryPayment(PaymentChannel.WECHAT, orderSn).providerPayload();
    }

    @PostMapping("/card/payments")
    public Map<String, Object> cardPayment(@RequestBody PaymentRequest request) {
        return paymentMockService.createPayment(request.withChannel(PaymentChannel.CREDIT_CARD)).providerPayload();
    }

    @GetMapping("/card/payments/{orderSn}")
    public Map<String, Object> cardQuery(@PathVariable String orderSn) {
        return paymentMockService.queryPayment(PaymentChannel.CREDIT_CARD, orderSn).providerPayload();
    }

    @PostMapping("/refunds")
    public Map<String, Object> refund(@RequestBody RefundRequest request) {
        RefundResponse response = paymentMockService.refund(request);
        return response.providerPayload();
    }
}

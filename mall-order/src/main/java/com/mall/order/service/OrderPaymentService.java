package com.mall.order.service;

import com.mall.order.vo.pay.CreateOrderPaymentRequest;
import com.mall.order.vo.pay.PaymentGatewayResponse;
import com.mall.order.vo.pay.PaymentNotifyRequest;
import com.mall.order.vo.pay.PaymentNotifyResult;
import com.mall.order.vo.pay.PaymentRefundGatewayResponse;
import com.mall.order.vo.pay.RefundOrderPaymentRequest;

import java.util.Date;

public interface OrderPaymentService {

    PaymentGatewayResponse createPayment(String orderSn, CreateOrderPaymentRequest request);

    PaymentGatewayResponse queryPayment(String channel, String orderSn);

    PaymentNotifyResult handleNotify(PaymentNotifyRequest request);

    PaymentRefundGatewayResponse refund(RefundOrderPaymentRequest request);

    int reconcilePendingPayments(Date createdBefore, int limit);
}

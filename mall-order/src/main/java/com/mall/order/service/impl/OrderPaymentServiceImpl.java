package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.order.client.PaymentGatewayClient;
import com.mall.order.config.PaymentGatewayProperties;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.entity.PaymentNotifyEventEntity;
import com.mall.order.entity.RefundInfoEntity;
import com.mall.order.service.OrderPaymentService;
import com.mall.order.service.OrderService;
import com.mall.order.service.PaymentInfoService;
import com.mall.order.service.PaymentNotifyEventService;
import com.mall.order.service.RefundInfoService;
import com.mall.order.util.PaySignUtils;
import com.mall.order.vo.pay.CreateOrderPaymentRequest;
import com.mall.order.vo.pay.PaymentGatewayRequest;
import com.mall.order.vo.pay.PaymentGatewayResponse;
import com.mall.order.vo.pay.PaymentNotifyRequest;
import com.mall.order.vo.pay.PaymentNotifyResult;
import com.mall.order.vo.pay.PaymentRefundGatewayRequest;
import com.mall.order.vo.pay.PaymentRefundGatewayResponse;
import com.mall.order.vo.pay.RefundOrderPaymentRequest;
import tools.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Service
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentServiceImpl.class);

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_CLOSED = "closed";
    private static final String STATUS_REFUNDED = "refunded";

    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentGatewayProperties paymentGatewayProperties;
    private final PaymentInfoService paymentInfoService;
    private final PaymentNotifyEventService paymentNotifyEventService;
    private final RefundInfoService refundInfoService;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public OrderPaymentServiceImpl(PaymentGatewayClient paymentGatewayClient,
                                   PaymentGatewayProperties paymentGatewayProperties,
                                   PaymentInfoService paymentInfoService,
                                   PaymentNotifyEventService paymentNotifyEventService,
                                   RefundInfoService refundInfoService,
                                   OrderService orderService,
                                   ObjectMapper objectMapper) {
        this.paymentGatewayClient = paymentGatewayClient;
        this.paymentGatewayProperties = paymentGatewayProperties;
        this.paymentInfoService = paymentInfoService;
        this.paymentNotifyEventService = paymentNotifyEventService;
        this.refundInfoService = refundInfoService;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentGatewayResponse createPayment(String orderSn, CreateOrderPaymentRequest request) {
        if (request == null || StringUtils.isBlank(request.channel())) {
            throw new IllegalArgumentException("payment channel is required");
        }
        OrderEntity order = requireOrder(orderSn);
        BigDecimal amount = order.getPayAmount() == null ? BigDecimal.ZERO : order.getPayAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("order pay amount must be positive");
        }
        PaymentGatewayResponse response = paymentGatewayClient.createPayment(new PaymentGatewayRequest(
                request.channel(),
                order.getOrderSn(),
                amount,
                request.currency(),
                StringUtils.defaultIfBlank(request.subject(), "Mall order " + order.getOrderSn()),
                request.description(),
                paymentGatewayProperties.notifyUrl(),
                paymentGatewayProperties.returnUrl(),
                request.clientIp(),
                request.cardToken()
        ));
        upsertPaymentInfo(order, response, null, false);
        return response;
    }

    @Override
    @Transactional
    public PaymentGatewayResponse queryPayment(String channel, String orderSn) {
        PaymentGatewayResponse response = paymentGatewayClient.queryPayment(channel, orderSn);
        OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order != null) {
            upsertPaymentInfo(order, response, null, false);
            applyTerminalOrderTransition(response);
        }
        return response;
    }

    @Override
    @Transactional
    public PaymentNotifyResult handleNotify(PaymentNotifyRequest request) {
        validateNotify(request);
        OrderEntity order = requireOrder(request.orderSn());
        String notifyStatus = normalizeNotifyStatus(request.tradeStatus());
        String eventKey = notifyEventKey(request);
        if (!paymentNotifyEventService.tryRecord(buildNotifyEvent(request, eventKey))) {
            return new PaymentNotifyResult(true, true, notifyStatus, "duplicate notify event");
        }

        PaymentInfoEntity existing = getPaymentInfo(request.orderSn());
        String currentStatus = existing == null ? null : existing.getPaymentStatus();
        if (STATUS_SUCCESS.equals(currentStatus) || STATUS_CLOSED.equals(currentStatus) || STATUS_REFUNDED.equals(currentStatus)) {
            paymentNotifyEventService.markProcessed(eventKey, "ignored", "order payment already " + currentStatus);
            return new PaymentNotifyResult(true, true, currentStatus, "already processed");
        }

        PaymentGatewayResponse response = new PaymentGatewayResponse(
                request.channel(),
                request.orderSn(),
                request.tradeNo(),
                notifyStatus,
                request.totalAmount(),
                request.currency(),
                existing == null ? "Mall order " + request.orderSn() : existing.getSubject(),
                null,
                null,
                null,
                true,
                request.signedContent(),
                request.sign(),
                null,
                null,
                null
        );
        upsertPaymentInfo(order, response, toJson(request), true);

        if (STATUS_SUCCESS.equals(response.status())) {
            orderService.payOrderSuccess(request.orderSn());
        } else if (STATUS_CLOSED.equals(response.status())) {
            orderService.closeOrder(request.orderSn());
        }
        paymentNotifyEventService.markProcessed(eventKey, "processed", "payment status " + response.status());
        return new PaymentNotifyResult(true, false, response.status(), "processed");
    }

    @Override
    @Transactional
    public PaymentRefundGatewayResponse refund(RefundOrderPaymentRequest request) {
        if (request == null || StringUtils.isBlank(request.channel())
                || StringUtils.isBlank(request.orderSn()) || StringUtils.isBlank(request.refundSn())) {
            throw new IllegalArgumentException("channel, orderSn and refundSn are required");
        }
        PaymentRefundGatewayResponse response = paymentGatewayClient.refund(new PaymentRefundGatewayRequest(
                request.channel(),
                request.orderSn(),
                request.tradeNo(),
                request.refundSn(),
                request.amount(),
                request.reason()
        ));
        PaymentInfoEntity existing = getPaymentInfo(response.orderSn());
        if (existing != null) {
            existing.setPaymentStatus(response.paymentStatus());
            existing.setCallbackContent(toJson(response));
            existing.setCallbackTime(new Date());
            paymentInfoService.updateById(existing);
        }
        upsertRefundInfo(response);
        return response;
    }

    @Override
    public int reconcilePendingPayments(Date createdBefore, int limit) {
        List<PaymentInfoEntity> pendingPayments = paymentInfoService.listPendingPaymentsForReconciliation(createdBefore, limit);
        int reconciled = 0;
        for (PaymentInfoEntity paymentInfo : pendingPayments) {
            if (paymentInfo == null
                    || StringUtils.isBlank(paymentInfo.getPaymentChannel())
                    || StringUtils.isBlank(paymentInfo.getOrderSn())) {
                continue;
            }
            try {
                PaymentGatewayResponse response = queryPayment(paymentInfo.getPaymentChannel(), paymentInfo.getOrderSn());
                if (isTerminalStatus(response.status())) {
                    reconciled++;
                }
            } catch (Exception e) {
                log.warn("支付主动查单失败: orderSn={} channel={}",
                        paymentInfo.getOrderSn(), paymentInfo.getPaymentChannel(), e);
            }
        }
        return reconciled;
    }

    private void upsertPaymentInfo(OrderEntity order, PaymentGatewayResponse response, String callbackContent, boolean callback) {
        PaymentInfoEntity entity = getPaymentInfo(order.getOrderSn());
        boolean insert = entity == null;
        if (insert) {
            entity = new PaymentInfoEntity();
            entity.setOrderSn(order.getOrderSn());
            entity.setOrderId(order.getId());
            entity.setCreateTime(new Date());
        }
        entity.setAlipayTradeNo(response.tradeNo());
        entity.setPaymentChannel(response.channel());
        entity.setTotalAmount(response.amount());
        entity.setPaymentCurrency(response.currency());
        entity.setSubject(response.subject());
        entity.setPaymentStatus(response.status());
        if (callback) {
            entity.setConfirmTime(new Date());
            entity.setCallbackTime(new Date());
        }
        if (StringUtils.isNotBlank(callbackContent)) {
            entity.setCallbackContent(callbackContent);
        } else if (response.providerPayload() != null) {
            entity.setCallbackContent(toJson(response.providerPayload()));
        }
        if (insert) {
            paymentInfoService.save(entity);
        } else {
            paymentInfoService.updateById(entity);
        }
    }

    private OrderEntity requireOrder(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            throw new IllegalArgumentException("orderSn is required");
        }
        OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order == null) {
            throw new IllegalArgumentException("order not found: " + orderSn);
        }
        return order;
    }

    private PaymentInfoEntity getPaymentInfo(String orderSn) {
        return paymentInfoService.getOne(new QueryWrapper<PaymentInfoEntity>().eq("order_sn", orderSn));
    }

    private void upsertRefundInfo(PaymentRefundGatewayResponse response) {
        RefundInfoEntity entity = refundInfoService.getOne(
                new QueryWrapper<RefundInfoEntity>().eq("refund_sn", response.refundSn())
        );
        boolean insert = entity == null;
        if (insert) {
            entity = new RefundInfoEntity();
        }
        entity.setOrderSn(response.orderSn());
        entity.setRefund(response.amount());
        entity.setRefundSn(response.refundSn());
        entity.setPaymentChannel(response.channel());
        entity.setTradeNo(response.tradeNo());
        entity.setRefundTradeNo(response.refundTradeNo());
        entity.setCurrency(response.currency());
        entity.setRefundStatus(1);
        entity.setRefundContent(toJson(response));
        if (insert) {
            refundInfoService.save(entity);
        } else {
            refundInfoService.updateById(entity);
        }
    }

    private void applyTerminalOrderTransition(PaymentGatewayResponse response) {
        if (STATUS_SUCCESS.equals(response.status())) {
            orderService.payOrderSuccess(response.orderSn());
        } else if (STATUS_CLOSED.equals(response.status())) {
            orderService.closeOrder(response.orderSn());
        }
    }

    private boolean isTerminalStatus(String status) {
        return STATUS_SUCCESS.equals(status) || STATUS_CLOSED.equals(status) || STATUS_REFUNDED.equals(status);
    }

    private void validateNotify(PaymentNotifyRequest request) {
        if (request == null || StringUtils.isAnyBlank(
                request.channel(), request.orderSn(), request.tradeNo(), request.tradeStatus(),
                request.signedContent(), request.sign())) {
            throw new IllegalArgumentException("invalid payment notify payload");
        }
        String expected = PaySignUtils.hmacSha256(request.signedContent(), paymentGatewayProperties.signKey());
        if (!StringUtils.equalsIgnoreCase(expected, request.sign())) {
            throw new IllegalArgumentException("payment notify signature invalid");
        }
    }

    private PaymentNotifyEventEntity buildNotifyEvent(PaymentNotifyRequest request, String eventKey) {
        PaymentNotifyEventEntity event = new PaymentNotifyEventEntity();
        event.setEventKey(eventKey);
        event.setChannel(request.channel());
        event.setOrderSn(request.orderSn());
        event.setTradeNo(request.tradeNo());
        event.setTradeStatus(request.tradeStatus());
        event.setTotalAmount(request.totalAmount());
        event.setCurrency(request.currency());
        event.setSignedContent(request.signedContent());
        event.setSign(request.sign());
        event.setNotifyTime(request.notifyTime());
        event.setRawContent(toJson(request));
        return event;
    }

    private String notifyEventKey(PaymentNotifyRequest request) {
        return request.channel().trim().toLowerCase(java.util.Locale.ROOT)
                + "|" + request.tradeNo().trim()
                + "|" + request.tradeStatus().trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String normalizeNotifyStatus(String tradeStatus) {
        String status = tradeStatus == null ? "" : tradeStatus.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (status) {
            case "TRADE_SUCCESS", "SUCCESS", "SUCCEEDED" -> STATUS_SUCCESS;
            case "TRADE_CLOSED", "CLOSED", "CANCELED" -> STATUS_CLOSED;
            case "REFUND", "REFUNDED", "TRADE_FINISHED" -> STATUS_REFUNDED;
            default -> "pending";
        };
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}

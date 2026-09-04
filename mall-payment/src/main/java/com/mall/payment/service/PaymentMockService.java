package com.mall.payment.service;

import com.mall.payment.config.PaymentMockProperties;
import com.mall.payment.model.PaymentChannel;
import com.mall.payment.model.PaymentRequest;
import com.mall.payment.model.PaymentResponse;
import com.mall.payment.model.PaymentStatus;
import com.mall.payment.model.RefundRequest;
import com.mall.payment.model.RefundResponse;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class PaymentMockService {

    private static final String DEFAULT_CURRENCY = "CNY";

    private final PaymentMockProperties properties;
    private final ConcurrentMap<String, PaymentRecord> payments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RefundRecord> refunds = new ConcurrentHashMap<>();

    public PaymentMockService(PaymentMockProperties properties) {
        this.properties = properties;
    }

    public PaymentResponse createPayment(PaymentRequest request) {
        validateCreate(request);
        PaymentChannel channel = request.channel();
        String orderSn = request.orderSn().trim();
        BigDecimal amount = normalizeAmount(request.amount());
        String currency = normalizeCurrency(request.currency());
        String key = paymentKey(channel, orderSn);

        PaymentRecord created = new PaymentRecord(
                channel,
                orderSn,
                tradeNo(channel, orderSn),
                amount,
                currency,
                blankToDefault(request.subject(), "Mall order " + orderSn),
                PaymentStatus.PENDING,
                Instant.now(),
                Instant.now()
        );
        PaymentRecord existing = payments.putIfAbsent(key, created);
        if (existing != null) {
            ensureSamePayment(existing, amount, currency);
            return toPaymentResponse(existing, true);
        }
        return toPaymentResponse(created, false);
    }

    public PaymentResponse queryPayment(PaymentChannel channel, String orderSn) {
        PaymentRecord record = requirePayment(channel, orderSn);
        return toPaymentResponse(record, true);
    }

    public PaymentResponse transition(PaymentChannel channel, String orderSn, PaymentStatus targetStatus) {
        if (targetStatus != PaymentStatus.SUCCESS && targetStatus != PaymentStatus.CLOSED) {
            throw new IllegalArgumentException("only success or close can be simulated");
        }
        PaymentRecord updated = payments.compute(paymentKey(channel, orderSn), (key, current) -> {
            if (current == null) {
                throw new IllegalArgumentException("payment not found: " + orderSn);
            }
            if (current.status() == PaymentStatus.REFUNDED) {
                return current;
            }
            return current.withStatus(targetStatus);
        });
        return toPaymentResponse(updated, true);
    }

    public RefundResponse refund(RefundRequest request) {
        validateRefund(request);
        PaymentChannel channel = request.channel();
        PaymentRecord payment = requirePayment(channel, request.orderSn());
        if (payment.status() != PaymentStatus.SUCCESS && payment.status() != PaymentStatus.REFUNDED) {
            throw new IllegalArgumentException("only successful payment can be refunded");
        }
        BigDecimal amount = normalizeAmount(request.amount());
        if (amount.compareTo(payment.amount()) > 0) {
            throw new IllegalArgumentException("refund amount cannot exceed payment amount");
        }
        String refundSn = request.refundSn().trim();
        String key = refundKey(channel, refundSn);

        RefundRecord created = new RefundRecord(
                channel,
                payment.orderSn(),
                payment.tradeNo(),
                refundSn,
                refundTradeNo(channel, refundSn),
                amount,
                payment.currency(),
                Instant.now()
        );
        RefundRecord existing = refunds.putIfAbsent(key, created);
        RefundRecord effective = existing == null ? created : existing;
        ensureSameRefund(effective, payment.orderSn(), amount);
        payments.put(paymentKey(channel, payment.orderSn()), payment.withStatus(PaymentStatus.REFUNDED));
        return toRefundResponse(effective, existing != null);
    }

    public Map<String, Object> buildNotifyPayload(PaymentResponse payment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channel", payment.channel().getCode());
        payload.put("orderSn", payment.orderSn());
        payload.put("tradeNo", payment.tradeNo());
        payload.put("tradeStatus", providerStatus(payment.channel(), payment.status()));
        payload.put("totalAmount", payment.amount().toPlainString());
        payload.put("currency", payment.currency());
        payload.put("notifyTime", payment.updatedAt().toString());
        payload.put("signedContent", payment.signedContent());
        payload.put("sign", payment.sign());
        return payload;
    }

    public String exportReconciliationCsv(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("reconciliation date is required");
        }
        List<StatementRow> rows = new ArrayList<>();
        for (PaymentRecord payment : payments.values()) {
            if (sameUtcDate(payment.updatedAt(), date)) {
                rows.add(new StatementRow(
                        "PAYMENT",
                        date,
                        payment.channel().getCode(),
                        payment.orderSn(),
                        payment.tradeNo(),
                        "",
                        payment.amount(),
                        payment.currency(),
                        payment.status().getCode(),
                        payment.updatedAt()
                ));
            }
        }
        for (RefundRecord refund : refunds.values()) {
            if (sameUtcDate(refund.createdAt(), date)) {
                rows.add(new StatementRow(
                        "REFUND",
                        date,
                        refund.channel().getCode(),
                        refund.orderSn(),
                        refund.tradeNo(),
                        refund.refundSn(),
                        refund.amount(),
                        refund.currency(),
                        PaymentStatus.REFUNDED.getCode(),
                        refund.createdAt()
                ));
            }
        }
        rows.sort(Comparator
                .comparing(StatementRow::happenedAt)
                .thenComparing(StatementRow::rowType)
                .thenComparing(StatementRow::orderSn)
                .thenComparing(StatementRow::refundSn));

        StringBuilder csv = new StringBuilder();
        csv.append("row_type,reconcile_date,channel,order_sn,trade_no,refund_sn,amount,currency,status,happened_at\n");
        for (StatementRow row : rows) {
            appendCsvRow(csv,
                    row.rowType(),
                    row.reconcileDate().toString(),
                    row.channel(),
                    row.orderSn(),
                    row.tradeNo(),
                    row.refundSn(),
                    row.amount().toPlainString(),
                    row.currency(),
                    row.status(),
                    row.happenedAt().toString()
            );
        }
        return csv.toString();
    }

    private PaymentResponse toPaymentResponse(PaymentRecord record, boolean idempotent) {
        String signedContent = "channel=" + record.channel().getCode()
                + "&orderSn=" + record.orderSn()
                + "&tradeNo=" + record.tradeNo()
                + "&status=" + record.status().getCode()
                + "&amount=" + record.amount().toPlainString()
                + "&currency=" + record.currency();
        String sign = hmacSha256(signedContent);
        ChannelPayload payload = providerPaymentPayload(record, signedContent, sign);
        return new PaymentResponse(
                record.channel(),
                record.orderSn(),
                record.tradeNo(),
                record.status(),
                record.amount(),
                record.currency(),
                record.subject(),
                payload.payUrl(),
                payload.qrCode(),
                payload.prepayId(),
                idempotent,
                signedContent,
                sign,
                payload.body(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private RefundResponse toRefundResponse(RefundRecord record, boolean idempotent) {
        String signedContent = "channel=" + record.channel().getCode()
                + "&orderSn=" + record.orderSn()
                + "&tradeNo=" + record.tradeNo()
                + "&refundSn=" + record.refundSn()
                + "&refundTradeNo=" + record.refundTradeNo()
                + "&amount=" + record.amount().toPlainString()
                + "&currency=" + record.currency();
        String sign = hmacSha256(signedContent);
        Map<String, Object> payload = providerRefundPayload(record, signedContent, sign);
        return new RefundResponse(
                record.channel(),
                record.orderSn(),
                record.tradeNo(),
                record.refundSn(),
                record.refundTradeNo(),
                PaymentStatus.REFUNDED,
                record.amount(),
                record.currency(),
                idempotent,
                signedContent,
                sign,
                payload,
                record.createdAt()
        );
    }

    private ChannelPayload providerPaymentPayload(PaymentRecord record, String signedContent, String sign) {
        return switch (record.channel()) {
            case ALIPAY -> alipayPaymentPayload(record, signedContent, sign);
            case WECHAT -> wechatPaymentPayload(record, signedContent, sign);
            case CREDIT_CARD -> cardPaymentPayload(record, signedContent, sign);
        };
    }

    private ChannelPayload alipayPaymentPayload(PaymentRecord record, String signedContent, String sign) {
        String qrCode = "https://qr.alipay.com/mock/" + record.tradeNo();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("code", "10000");
        response.put("msg", "Success");
        response.put("app_id", properties.alipayAppId());
        response.put("out_trade_no", record.orderSn());
        response.put("trade_no", record.tradeNo());
        response.put("total_amount", record.amount().toPlainString());
        response.put("subject", record.subject());
        response.put("trade_status", providerStatus(record.channel(), record.status()));
        response.put("qr_code", qrCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("alipay_trade_precreate_response", response);
        body.put("sign_type", "HMAC-SHA256");
        body.put("signed_content", signedContent);
        body.put("sign", sign);
        return new ChannelPayload(properties.gatewayBaseUrl() + "/payment/provider-mock/alipay/trade/precreate", qrCode, null, body);
    }

    private ChannelPayload wechatPaymentPayload(PaymentRecord record, String signedContent, String sign) {
        String prepayId = "wxprepay_" + record.tradeNo();
        String codeUrl = "weixin://wxpay/bizpayurl?pr=" + prepayId;
        Map<String, Object> amount = new LinkedHashMap<>();
        amount.put("total", cents(record.amount()));
        amount.put("currency", record.currency());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("appid", properties.wechatAppId());
        body.put("mchid", properties.wechatMchId());
        body.put("description", record.subject());
        body.put("out_trade_no", record.orderSn());
        body.put("transaction_id", record.tradeNo());
        body.put("trade_state", providerStatus(record.channel(), record.status()));
        body.put("amount", amount);
        body.put("prepay_id", prepayId);
        body.put("code_url", codeUrl);
        body.put("signed_content", signedContent);
        body.put("signature", sign);
        return new ChannelPayload(codeUrl, codeUrl, prepayId, body);
    }

    private ChannelPayload cardPaymentPayload(PaymentRecord record, String signedContent, String sign) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", record.tradeNo());
        body.put("object", "payment_intent");
        body.put("order_sn", record.orderSn());
        body.put("amount", cents(record.amount()));
        body.put("currency", record.currency().toLowerCase(java.util.Locale.ROOT));
        body.put("status", providerStatus(record.channel(), record.status()));
        body.put("authorization_code", "AUTH" + Math.abs(record.tradeNo().hashCode()));
        body.put("signed_content", signedContent);
        body.put("signature", sign);
        return new ChannelPayload(null, null, null, body);
    }

    private Map<String, Object> providerRefundPayload(RefundRecord record, String signedContent, String sign) {
        Map<String, Object> body = new LinkedHashMap<>();
        switch (record.channel()) {
            case ALIPAY -> {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("code", "10000");
                response.put("msg", "Success");
                response.put("out_trade_no", record.orderSn());
                response.put("trade_no", record.tradeNo());
                response.put("out_request_no", record.refundSn());
                response.put("refund_fee", record.amount().toPlainString());
                response.put("fund_change", "Y");
                body.put("alipay_trade_refund_response", response);
                body.put("sign", sign);
            }
            case WECHAT -> {
                body.put("refund_id", record.refundTradeNo());
                body.put("out_refund_no", record.refundSn());
                body.put("transaction_id", record.tradeNo());
                body.put("out_trade_no", record.orderSn());
                body.put("status", "SUCCESS");
                body.put("amount", Map.of("refund", cents(record.amount()), "currency", record.currency()));
                body.put("signature", sign);
            }
            case CREDIT_CARD -> {
                body.put("id", record.refundTradeNo());
                body.put("object", "refund");
                body.put("payment_id", record.tradeNo());
                body.put("order_sn", record.orderSn());
                body.put("amount", cents(record.amount()));
                body.put("currency", record.currency().toLowerCase(java.util.Locale.ROOT));
                body.put("status", "succeeded");
                body.put("signature", sign);
            }
        }
        body.put("signed_content", signedContent);
        return body;
    }

    private String providerStatus(PaymentChannel channel, PaymentStatus status) {
        return switch (channel) {
            case ALIPAY -> switch (status) {
                case PENDING -> "WAIT_BUYER_PAY";
                case SUCCESS -> "TRADE_SUCCESS";
                case CLOSED -> "TRADE_CLOSED";
                case REFUNDED -> "TRADE_FINISHED";
            };
            case WECHAT -> switch (status) {
                case PENDING -> "NOTPAY";
                case SUCCESS -> "SUCCESS";
                case CLOSED -> "CLOSED";
                case REFUNDED -> "REFUND";
            };
            case CREDIT_CARD -> switch (status) {
                case PENDING -> "requires_confirmation";
                case SUCCESS -> "succeeded";
                case CLOSED -> "canceled";
                case REFUNDED -> "refunded";
            };
        };
    }

    private PaymentRecord requirePayment(PaymentChannel channel, String orderSn) {
        if (channel == null) {
            throw new IllegalArgumentException("payment channel is required");
        }
        if (orderSn == null || orderSn.isBlank()) {
            throw new IllegalArgumentException("orderSn is required");
        }
        PaymentRecord record = payments.get(paymentKey(channel, orderSn));
        if (record == null) {
            throw new IllegalArgumentException("payment not found: " + orderSn);
        }
        return record;
    }

    private void validateCreate(PaymentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("payment request is required");
        }
        if (request.channel() == null) {
            throw new IllegalArgumentException("payment channel is required");
        }
        if (request.orderSn() == null || request.orderSn().isBlank()) {
            throw new IllegalArgumentException("orderSn is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private void validateRefund(RefundRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("refund request is required");
        }
        if (request.channel() == null) {
            throw new IllegalArgumentException("payment channel is required");
        }
        if (request.orderSn() == null || request.orderSn().isBlank()) {
            throw new IllegalArgumentException("orderSn is required");
        }
        if (request.refundSn() == null || request.refundSn().isBlank()) {
            throw new IllegalArgumentException("refundSn is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }

    private void ensureSamePayment(PaymentRecord existing, BigDecimal amount, String currency) {
        if (existing.amount().compareTo(amount) != 0 || !Objects.equals(existing.currency(), currency)) {
            throw new IllegalArgumentException("idempotent payment request conflicts with existing payment");
        }
    }

    private void ensureSameRefund(RefundRecord existing, String orderSn, BigDecimal amount) {
        if (!Objects.equals(existing.orderSn(), orderSn) || existing.amount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("idempotent refund request conflicts with existing refund");
        }
    }

    private String paymentKey(PaymentChannel channel, String orderSn) {
        return channel.getCode() + ":" + orderSn.trim();
    }

    private String refundKey(PaymentChannel channel, String refundSn) {
        return channel.getCode() + ":" + refundSn.trim();
    }

    private String tradeNo(PaymentChannel channel, String orderSn) {
        String prefix = switch (channel) {
            case ALIPAY -> "ALI";
            case WECHAT -> "WX";
            case CREDIT_CARD -> "CARD";
        };
        return prefix + orderSn.replaceAll("[^A-Za-z0-9]", "");
    }

    private String refundTradeNo(PaymentChannel channel, String refundSn) {
        String prefix = switch (channel) {
            case ALIPAY -> "ALIR";
            case WECHAT -> "WXR";
            case CREDIT_CARD -> "CARDR";
        };
        return prefix + refundSn.replaceAll("[^A-Za-z0-9]", "");
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? DEFAULT_CURRENCY : currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int cents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private String hmacSha256(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.signKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("failed to sign payment payload", e);
        }
    }

    private boolean sameUtcDate(Instant instant, LocalDate date) {
        return LocalDate.ofInstant(instant, ZoneOffset.UTC).equals(date);
    }

    private void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values[i]));
        }
        csv.append('\n');
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private record PaymentRecord(
            PaymentChannel channel,
            String orderSn,
            String tradeNo,
            BigDecimal amount,
            String currency,
            String subject,
            PaymentStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {

        PaymentRecord withStatus(PaymentStatus status) {
            return new PaymentRecord(channel, orderSn, tradeNo, amount, currency, subject, status, createdAt, Instant.now());
        }
    }

    private record RefundRecord(
            PaymentChannel channel,
            String orderSn,
            String tradeNo,
            String refundSn,
            String refundTradeNo,
            BigDecimal amount,
            String currency,
            Instant createdAt
    ) {
    }

    private record ChannelPayload(
            String payUrl,
            String qrCode,
            String prepayId,
            Map<String, Object> body
    ) {
    }

    private record StatementRow(
            String rowType,
            LocalDate reconcileDate,
            String channel,
            String orderSn,
            String tradeNo,
            String refundSn,
            BigDecimal amount,
            String currency,
            String status,
            Instant happenedAt
    ) {
    }
}

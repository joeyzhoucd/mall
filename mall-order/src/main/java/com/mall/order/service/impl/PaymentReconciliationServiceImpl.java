package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.order.client.PaymentGatewayClient;
import com.mall.order.entity.PaymentInfoEntity;
import com.mall.order.entity.PaymentReconciliationResultEntity;
import com.mall.order.entity.RefundInfoEntity;
import com.mall.order.service.PaymentInfoService;
import com.mall.order.service.PaymentReconciliationResultService;
import com.mall.order.service.PaymentReconciliationService;
import com.mall.order.service.RefundInfoService;
import com.mall.order.vo.pay.PaymentReconciliationSummary;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private static final String ROW_PAYMENT = "PAYMENT";
    private static final String ROW_REFUND = "REFUND";
    private static final String DIFF_MATCH = "MATCH";
    private static final String STATUS_RESOLVED = "resolved";
    private static final String STATUS_PENDING = "pending";

    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentInfoService paymentInfoService;
    private final RefundInfoService refundInfoService;
    private final PaymentReconciliationResultService resultService;

    public PaymentReconciliationServiceImpl(PaymentGatewayClient paymentGatewayClient,
                                            PaymentInfoService paymentInfoService,
                                            RefundInfoService refundInfoService,
                                            PaymentReconciliationResultService resultService) {
        this.paymentGatewayClient = paymentGatewayClient;
        this.paymentInfoService = paymentInfoService;
        this.refundInfoService = refundInfoService;
        this.resultService = resultService;
    }

    @Override
    @Transactional
    public PaymentReconciliationSummary reconcile(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("reconcile date is required");
        }
        String csv = paymentGatewayClient.downloadReconciliationFile(date);
        List<StatementRow> rows = parseRows(csv);
        int matched = 0;
        int different = 0;
        for (StatementRow row : rows) {
            PaymentReconciliationResultEntity result = switch (row.rowType()) {
                case ROW_PAYMENT -> reconcilePayment(row);
                case ROW_REFUND -> reconcileRefund(row);
                default -> buildResult(row, null, null, null, "UNSUPPORTED_ROW_TYPE");
            };
            resultService.saveOrUpdateResult(result);
            if (DIFF_MATCH.equals(result.getDifferenceType())) {
                matched++;
            } else {
                different++;
            }
        }
        return new PaymentReconciliationSummary(date, rows.size(), matched, different);
    }

    private PaymentReconciliationResultEntity reconcilePayment(StatementRow row) {
        PaymentInfoEntity local = paymentInfoService.getOne(new QueryWrapper<PaymentInfoEntity>()
                .eq("order_sn", row.orderSn())
                .eq("payment_channel", row.channel()));
        if (local == null) {
            return buildResult(row, null, null, null, "LOCAL_MISSING");
        }
        String difference = firstDifference(
                amountEquals(row.amount(), local.getTotalAmount()) ? null : "AMOUNT_MISMATCH",
                StringUtils.equalsIgnoreCase(row.status(), local.getPaymentStatus()) ? null : "STATUS_MISMATCH",
                StringUtils.equalsIgnoreCase(row.currency(), local.getPaymentCurrency()) ? null : "CURRENCY_MISMATCH",
                StringUtils.equals(row.tradeNo(), local.getAlipayTradeNo()) ? null : "TRADE_NO_MISMATCH"
        );
        return buildResult(row, local.getTotalAmount(), local.getPaymentStatus(), local.getPaymentCurrency(), difference);
    }

    private PaymentReconciliationResultEntity reconcileRefund(StatementRow row) {
        RefundInfoEntity local = refundInfoService.getOne(new QueryWrapper<RefundInfoEntity>()
                .eq("refund_sn", row.refundSn()));
        if (local == null) {
            return buildResult(row, null, null, null, "LOCAL_MISSING");
        }
        String localStatus = localRefundStatus(local.getRefundStatus());
        String difference = firstDifference(
                StringUtils.equals(row.orderSn(), local.getOrderSn()) ? null : "ORDER_SN_MISMATCH",
                StringUtils.equalsIgnoreCase(row.channel(), local.getPaymentChannel()) ? null : "CHANNEL_MISMATCH",
                StringUtils.equals(row.tradeNo(), local.getTradeNo()) ? null : "TRADE_NO_MISMATCH",
                amountEquals(row.amount(), local.getRefund()) ? null : "AMOUNT_MISMATCH",
                StringUtils.equalsIgnoreCase(row.status(), localStatus) ? null : "STATUS_MISMATCH",
                StringUtils.equalsIgnoreCase(row.currency(), local.getCurrency()) ? null : "CURRENCY_MISMATCH"
        );
        return buildResult(row, local.getRefund(), localStatus, local.getCurrency(), difference);
    }

    private PaymentReconciliationResultEntity buildResult(StatementRow row,
                                                          BigDecimal localAmount,
                                                          String localStatus,
                                                          String localCurrency,
                                                          String differenceType) {
        PaymentReconciliationResultEntity result = new PaymentReconciliationResultEntity();
        result.setReconcileDate(row.reconcileDate());
        result.setRowType(row.rowType());
        result.setChannel(row.channel());
        result.setOrderSn(row.orderSn());
        result.setTradeNo(row.tradeNo());
        result.setRefundSn(StringUtils.defaultIfBlank(row.refundSn(), null));
        result.setGatewayAmount(row.amount());
        result.setLocalAmount(localAmount);
        result.setGatewayStatus(row.status());
        result.setLocalStatus(localStatus);
        result.setCurrency(StringUtils.defaultIfBlank(localCurrency, row.currency()));
        result.setDifferenceType(StringUtils.defaultIfBlank(differenceType, DIFF_MATCH));
        result.setProcessStatus(DIFF_MATCH.equals(result.getDifferenceType()) ? STATUS_RESOLVED : STATUS_PENDING);
        result.setRawLine(row.rawLine());
        return result;
    }

    private List<StatementRow> parseRows(String csv) {
        List<StatementRow> rows = new ArrayList<>();
        if (StringUtils.isBlank(csv)) {
            return rows;
        }
        String[] lines = csv.split("\\r?\\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (StringUtils.isBlank(line)) {
                continue;
            }
            List<String> fields = parseCsvLine(line);
            if (fields.size() != 10) {
                throw new IllegalArgumentException("invalid reconciliation row at line " + (i + 1));
            }
            rows.add(new StatementRow(
                    fields.get(0),
                    LocalDate.parse(fields.get(1)),
                    fields.get(2),
                    fields.get(3),
                    fields.get(4),
                    fields.get(5),
                    normalizeAmount(new BigDecimal(fields.get(6))),
                    fields.get(7),
                    fields.get(8),
                    line
            ));
        }
        return rows;
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private boolean amountEquals(BigDecimal gateway, BigDecimal local) {
        return gateway != null
                && local != null
                && normalizeAmount(gateway).compareTo(normalizeAmount(local)) == 0;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String firstDifference(String... differences) {
        for (String difference : differences) {
            if (difference != null) {
                return difference;
            }
        }
        return DIFF_MATCH;
    }

    private String localRefundStatus(Integer refundStatus) {
        if (refundStatus == null) {
            return null;
        }
        return refundStatus == 1 ? "refunded" : "pending";
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
            String rawLine
    ) {
    }
}

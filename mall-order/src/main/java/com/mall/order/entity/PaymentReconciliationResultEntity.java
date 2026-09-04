package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;

@Data
@TableName("oms_payment_reconciliation_result")
public class PaymentReconciliationResultEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private LocalDate reconcileDate;

    private String rowType;

    private String channel;

    private String orderSn;

    private String tradeNo;

    private String refundSn;

    private BigDecimal gatewayAmount;

    private BigDecimal localAmount;

    private String gatewayStatus;

    private String localStatus;

    private String currency;

    private String differenceType;

    private String processStatus;

    private String rawLine;

    private Date createTime;

    private Date updateTime;
}

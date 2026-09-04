package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("oms_payment_notify_event")
public class PaymentNotifyEventEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private String eventKey;

    private String channel;

    private String orderSn;

    private String tradeNo;

    private String tradeStatus;

    private BigDecimal totalAmount;

    private String currency;

    private String signedContent;

    private String sign;

    private String notifyTime;

    private String rawContent;

    private String processStatus;

    private String processMessage;

    private Date createTime;

    private Date updateTime;
}

package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("oms_order_outbox_message")
public class OrderOutboxMessageEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private String messageKey;

    private String businessType;

    private String businessKey;

    private String exchangeName;

    private String routingKey;

    private String payloadType;

    private String payload;

    private Integer status;

    private Integer retryCount;

    private Date nextRetryTime;

    private String lastError;

    private Date sentTime;

    private Date createTime;

    private Date updateTime;
}

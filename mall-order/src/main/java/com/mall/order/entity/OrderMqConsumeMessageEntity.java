package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
@TableName("oms_mq_consume_message")
public class OrderMqConsumeMessageEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private String consumerGroup;

    private String messageKey;

    private String businessType;

    private Integer status;

    private Integer consumeCount;

    private String lastError;

    private Date successTime;

    private Date createTime;

    private Date updateTime;
}

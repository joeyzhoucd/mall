package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 秒杀本地消息表（事务性发件箱）：秒杀抢购成功后先写这一行做审计留痕，
 * 再发 MQ，MQ confirm 成功才把 status 改成已发送，消费者建单成功后回填 order_sn。
 */
@Data
@TableName("sms_seckill_local_message")
public class SeckillLocalMessageEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private Long relationId;

    private Long memberId;

    private String username;

    private Long skuId;

    private String skuName;

    private String skuPic;

    private BigDecimal seckillPrice;

    private Long addrId;

    private String orderSn;

    private Integer status;

    private Integer retryCount;

    private Date createTime;

    private Date updateTime;
}

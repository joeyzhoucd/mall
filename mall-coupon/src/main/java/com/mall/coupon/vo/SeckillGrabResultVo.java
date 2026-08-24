package com.mall.coupon.vo;

import lombok.Data;

@Data
public class SeckillGrabResultVo {
    private boolean success;
    private boolean hasDefaultAddress;
    private Long messageId;
    private com.mall.common.constant.ErrorCode failReason;
}

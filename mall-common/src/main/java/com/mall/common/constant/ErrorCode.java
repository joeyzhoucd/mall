package com.mall.common.constant;

public enum ErrorCode {
    ORDER_NOT_FOUND(20001, "订单不存在"),
    PAY_SIGN_INVALID(20002, "签名校验失败"),
    ORDER_STATUS_TRANSITION_ILLEGAL(20003, "illegal order status transition"),
    STOCK_NOT_ENOUGH(21001, "库存不足"),
    SECKILL_SOLD_OUT(22001, "已经卖光了，下次再来"),
    SECKILL_ALREADY_GRABBED(22002, "你已经抢到过了"),
    SECKILL_NOT_ACTIVE(22003, "活动还没开始或已下线"),
    SECKILL_MQ_FAILED(22004, "抢购失败，请重试"),
    SECKILL_MESSAGE_INVALID(22005, "抢购记录不存在或状态不对"),
    SECKILL_FORBIDDEN(22006, "无权限"),
    SECKILL_SYSTEM_ERROR(22007, "系统繁忙，请重试"),
    REQUEST_FAILED(10000, "请求失败");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}


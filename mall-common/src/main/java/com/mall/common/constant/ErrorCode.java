package com.mall.common.constant;

public enum ErrorCode {
    ORDER_NOT_FOUND(20001, "订单不存在"),
    PAY_SIGN_INVALID(20002, "签名校验失败"),
    STOCK_NOT_ENOUGH(21001, "库存不足"),
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


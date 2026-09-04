package com.mall.payment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum PaymentChannel {
    ALIPAY("alipay"),
    WECHAT("wechat"),
    CREDIT_CARD("credit_card");

    private final String code;

    PaymentChannel(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static PaymentChannel from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payment channel is required");
        }
        String normalized = value.trim().replace("-", "_").toUpperCase(Locale.ROOT);
        if ("CARD".equals(normalized) || "CREDITCARD".equals(normalized)) {
            normalized = "CREDIT_CARD";
        }
        return PaymentChannel.valueOf(normalized);
    }
}

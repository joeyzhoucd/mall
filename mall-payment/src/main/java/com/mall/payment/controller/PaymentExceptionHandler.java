package com.mall.payment.controller;

import com.mall.common.utils.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// 2026-09-13 显式定序：见 mall-common 的 UnhandledExceptionAdvice。
// @ControllerAdvice 不写 @Order 时默认是 LOWEST_PRECEDENCE，和那个兜底并列，
// 而并列时顺序不确定 —— 可能让兜底抢走本该由这里处理的 IllegalArgumentException
//（表现：本来该是带具体原因的 400，变成一句"服务暂时不可用"的 500）。
@Order(0)
@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R badRequest(IllegalArgumentException ex) {
        return R.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }
}

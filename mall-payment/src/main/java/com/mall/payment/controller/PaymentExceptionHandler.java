package com.mall.payment.controller;

import com.mall.common.utils.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R badRequest(IllegalArgumentException ex) {
        return R.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }
}

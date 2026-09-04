package com.mall.order.controller;

import com.mall.common.utils.R;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice(assignableTypes = OrderPaymentController.class)
public class OrderPaymentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R badRequest(IllegalArgumentException ex) {
        return R.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage());
    }

    @ExceptionHandler({IllegalStateException.class, RestClientException.class})
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public R paymentGatewayFailed(Exception ex) {
        return R.error(HttpStatus.BAD_GATEWAY.value(), "payment gateway request failed: " + ex.getMessage());
    }
}

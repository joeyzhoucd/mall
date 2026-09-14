package com.mall.order.controller;

import com.mall.common.utils.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

// 2026-09-13 显式定序：见 mall-common 的 UnhandledExceptionAdvice。
//
// 【这一个本来就相对安全，但仍然值得写上】它用了 assignableTypes 限定，
// 只对 OrderPaymentController 生效 —— 解析器在选 advice 时会先按适用范围筛，
// 所以在那个控制器之外它压根不参与。但在【那个控制器内部】它和兜底仍然并列，
// 而并列时顺序不确定。写成 0 之后就是确定的：这里先，兜底最后。
@Order(0)
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

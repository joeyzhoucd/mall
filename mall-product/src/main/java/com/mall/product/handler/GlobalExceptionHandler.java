package com.mall.product.handler;

import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

// 2026-09-13 显式定序：mall-common 里新增了一个兜底的 UnhandledExceptionAdvice
//（@Order(LOWEST_PRECEDENCE)，声明的是 Exception）。而 @ControllerAdvice 不写
// @Order 时默认【也是】LOWEST_PRECEDENCE —— 并列时顺序不确定，
// 而解析器是「按顺序找第一个有可用方法的 advice」，并列就可能让兜底那个
// 抢走本该由这里处理的校验异常（表现：400 变成 500，且错误信息丢失）。
// 写成 0 让顺序变成确定的：这里先，兜底最后。
//
// 【这个类的名字现在有误导性】它叫 GlobalExceptionHandler，但只处理两种
// 参数校验异常，真正"global"的是 mall-common 那个。没改名是因为改名会动
// 一个和本次改动无关的文件；记在这里免得下次看到名字就以为兜底已经有了。
@Order(0)
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BindException.class)
    public ResponseEntity<?> handleBindException(BindException e) {
        String msg = e.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(msg);
    }
}

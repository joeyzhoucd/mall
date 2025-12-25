package com.mall.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CartVo {

    private List<CartItemVo> items = new ArrayList<>();

    private BigDecimal reduce = BigDecimal.ZERO;

    public Integer getCountNum() {
        return items.stream().mapToInt(item -> item.getCount() == null ? 0 : item.getCount()).sum();
    }

    public Integer getCheckNum() {
        return items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCheck()))
                .mapToInt(item -> item.getCount() == null ? 0 : item.getCount())
                .sum();
    }

    public BigDecimal getTotalAmount() {
        BigDecimal total = items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCheck()))
                .map(CartItemVo::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.subtract(reduce == null ? BigDecimal.ZERO : reduce);
    }
}


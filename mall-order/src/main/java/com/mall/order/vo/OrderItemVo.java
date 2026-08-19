package com.mall.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderItemVo {
    private Long skuId;
    private String title;
    private String image;
    private List<String> skuAttr;
    private BigDecimal price;
    private Integer count;

    public BigDecimal getTotalPrice() {
        if (price == null || count == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(new BigDecimal(count));
    }
}


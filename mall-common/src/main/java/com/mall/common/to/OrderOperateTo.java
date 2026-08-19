package com.mall.common.to;

import lombok.Data;

@Data
public class OrderOperateTo {
    private String orderSn;
    private Integer status;
    private String note;
    private String operateMan;
}


package com.mall.common.to;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SeckillOrderTo {
    private Long localMessageId;
    private Long relationId;
    private Long memberId;
    private String username;
    private Long skuId;
    private String skuName;
    private String skuPic;
    private BigDecimal seckillPrice;
    private Long addrId;
}

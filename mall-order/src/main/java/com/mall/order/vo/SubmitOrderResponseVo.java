package com.mall.order.vo;

import com.mall.order.entity.OrderEntity;
import lombok.Data;

@Data
public class SubmitOrderResponseVo {
    /**
     * 0 success, 1 token invalid, 2 price changed, 3 stock locked failed
     */
    private Integer code;
    private OrderEntity order;
}


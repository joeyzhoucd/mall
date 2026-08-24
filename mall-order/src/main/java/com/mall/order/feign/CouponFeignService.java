package com.mall.order.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("mall-coupon")
public interface CouponFeignService {

    @PostMapping("/coupon/seckill/message/{messageId}/order-created")
    R handleOrderCreated(@PathVariable("messageId") Long messageId, @RequestParam("orderSn") String orderSn,
                          @RequestHeader("X-Seckill-Internal-Token") String internalToken);
}

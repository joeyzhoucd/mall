package com.mall.ware.feign;

import com.mall.common.utils.R;
import com.mall.common.to.OrderOperateTo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("mall-order")
public interface OrderFeignService {

    @GetMapping("/order/order/status/{orderSn}")
    R getOrderStatus(@PathVariable("orderSn") String orderSn);

    @PostMapping("/order/order/operate")
    R recordOperate(@RequestBody OrderOperateTo operateTo);
}


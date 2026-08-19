package com.mall.order.feign;

import com.mall.common.utils.R;
import com.mall.order.config.OrderFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(value = "mall-cart", configuration = OrderFeignConfig.class)
public interface CartFeignService {

    @GetMapping("/currentUserCartItems")
    R getCurrentUserCartItems();

    @PostMapping("/deleteItems")
    R deleteItems(@RequestBody List<Long> skuIds);
}


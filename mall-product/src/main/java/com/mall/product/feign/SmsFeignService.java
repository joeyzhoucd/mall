package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient("mall-coupon") // Feign client for coupon service
public interface SmsFeignService {

    
    @PostMapping("/coupon/spubounds/saveFromMap")
    R saveSpuBounds(@RequestBody Map<String, String> params);

    
    @PostMapping("/coupon/skuladder/saveFromMap")
    R saveSkuLadder(@RequestBody Map<String, String> params);

    
    @PostMapping("/coupon/skufullreduction/saveFromMap")
    R saveSkuFullReduction(@RequestBody Map<String, String> params);

    
    @PostMapping("/coupon/memberprice/saveFromMap")
    R saveSkuMemberPrice(@RequestBody Map<String, String> params);
}
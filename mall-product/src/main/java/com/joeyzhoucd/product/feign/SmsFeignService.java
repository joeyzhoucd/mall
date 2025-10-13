package com.joeyzhoucd.product.feign;

import com.joeyzhoucd.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient("mall-coupon") // 指定要调用的服务名
public interface SmsFeignService {

    /**
     * 保存SPU积分信息
     * @param params 包含spuId, buyBounds, growBounds
     * @return
     */
    @PostMapping("/coupon/spubounds/saveFromMap")
    R saveSpuBounds(@RequestBody Map<String, String> params);

    /**
     * 保存SKU阶梯价格
     * @param params 包含skuId, fullCount, discount, price, addOther
     * @return
     */
    @PostMapping("/coupon/skuladder/saveFromMap")
    R saveSkuLadder(@RequestBody Map<String, String> params);

    /**
     * 保存SKU满减信息
     * @param params 包含skuId, fullPrice, reducePrice, addOther
     * @return
     */
    @PostMapping("/coupon/skufullreduction/saveFromMap")
    R saveSkuFullReduction(@RequestBody Map<String, String> params);

    /**
     * 保存SKU会员价格
     * @param params 包含skuId, memberLevelId, memberLevelName, memberPrice, addOther
     * @return
     */
    @PostMapping("/coupon/memberprice/saveFromMap")
    R saveSkuMemberPrice(@RequestBody Map<String, String> params);
}

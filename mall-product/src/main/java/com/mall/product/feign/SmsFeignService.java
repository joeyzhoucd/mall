package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient("mall-coupon") // æŒ‡å®šè¦è°ƒç”¨çš„æœåŠ¡å
public interface SmsFeignService {

    /**
     * ä¿å­˜SPUç§¯åˆ†ä¿¡æ¯
     * @param params åŒ…å«spuId, buyBounds, growBounds
     * @return
     */
    @PostMapping("/coupon/spubounds/saveFromMap")
    R saveSpuBounds(@RequestBody Map<String, String> params);

    /**
     * ä¿å­˜SKUé˜¶æ¢¯ä»·æ ¼
     * @param params åŒ…å«skuId, fullCount, discount, price, addOther
     * @return
     */
    @PostMapping("/coupon/skuladder/saveFromMap")
    R saveSkuLadder(@RequestBody Map<String, String> params);

    /**
     * ä¿å­˜SKUæ»¡å‡ä¿¡æ¯
     * @param params åŒ…å«skuId, fullPrice, reducePrice, addOther
     * @return
     */
    @PostMapping("/coupon/skufullreduction/saveFromMap")
    R saveSkuFullReduction(@RequestBody Map<String, String> params);

    /**
     * ä¿å­˜SKUä¼šå‘˜ä»·æ ¼
     * @param params åŒ…å«skuId, memberLevelId, memberLevelName, memberPrice, addOther
     * @return
     */
    @PostMapping("/coupon/memberprice/saveFromMap")
    R saveSkuMemberPrice(@RequestBody Map<String, String> params);
}

package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 「搭配购买」：mall-order 的内部接口，只给 SPU id 和分数。
 *
 * <p><b>单独的 contextId</b>：Feign 的超时按 client 名配置、而且配在所有服务共用的
 * mall-common-default.yml 里。直接用 {@code mall-order} 这个名字调短超时，
 * mall-ware 等调 mall-order 慢接口的地方会一起被截断。用 contextId = orderReco
 * 让这条调用有自己的一份超时（见 application.yml 的 spring.cloud.openfeign.client.config.orderReco）。
 */
@FeignClient(name = "mall-order", contextId = "orderReco")
public interface OrderRecoFeignService {

    @GetMapping("/order/internal/reco/complements/{spuId}")
    R complements(@PathVariable("spuId") Long spuId, @RequestParam("size") Integer size);
}

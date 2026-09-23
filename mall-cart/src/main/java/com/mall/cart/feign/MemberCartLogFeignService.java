package com.mall.cart.feign;

import com.mall.cart.to.CartLogTo;
import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 把购物车行为日志批量投给 mall-member 落库。
 *
 * <p>调用方是 {@link com.mall.cart.service.CartEventLogger} 的后台线程，
 * <b>绝不在用户请求线程上调用</b> —— 埋点不能拖慢加购。
 */
@FeignClient("mall-member")
public interface MemberCartLogFeignService {

    @PostMapping("/member/cartlog/batch")
    R batch(@RequestBody List<CartLogTo> list);
}

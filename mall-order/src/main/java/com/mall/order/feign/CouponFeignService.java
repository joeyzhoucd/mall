package com.mall.order.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@FeignClient("mall-coupon")
public interface CouponFeignService {

    @PostMapping("/coupon/seckill/message/{messageId}/order-created")
    R handleOrderCreated(@PathVariable("messageId") Long messageId, @RequestParam("orderSn") String orderSn,
                          @RequestHeader("X-Seckill-Internal-Token") String internalToken);

    // =====================================================================
    // 优惠券。全部走 /internal/*，必须带共享密钥 —— 网关那条 seckill.mall.com
    // 路由是完全公开的，没有密钥任何人都能伪造用券/退券。
    // =====================================================================

    /**
     * 结算页可选的券。门槛过滤在 mall-coupon 的 SQL 里做。
     * <p>
     * 这是<b>只读</b>调用。结算页本来就要调 mall-member 拿地址、调 mall-cart 拿商品，
     * 再多一次远程调用；结算页不是热点写路径，可以接受。
     */
    @GetMapping("/coupon/promotion/internal/usable")
    R usableCoupons(@RequestParam("memberId") Long memberId,
                    @RequestParam("amount") BigDecimal amount,
                    @RequestHeader("X-Seckill-Internal-Token") String internalToken);

    /**
     * 预览抵扣金额 —— <b>只读，不改状态</b>。
     *
     * <p><b>为什么下单要先 preview 再 use，多一次远程调用</b>：
     * 价格校验必须在改动券状态<b>之前</b>做。价格变动（{@code code 2}）和
     * 库存不足（{@code code 3}）都是<b>常见</b>失败，如果先 use 再校验价格，
     * 这两种常见路径都得走退券补偿。
     * <p>
     * 补偿是整条链上最脆弱的一环 —— 这个仓库被它坑过一次：
     * catch 里的补偿动作访问了一张缺失的表，异常穿透 catch，
     * 导致 {@code sendStockRelease} 永远没执行、锁定库存永久泄漏。
     * 用一次只读调用换掉两条补偿路径，是划算的。
     */
    @PostMapping("/coupon/promotion/internal/preview")
    R previewCoupon(@RequestParam("historyId") Long historyId,
                    @RequestParam("memberId") Long memberId,
                    @RequestParam("amount") BigDecimal amount,
                    @RequestHeader("X-Seckill-Internal-Token") String internalToken);

    /**
     * 用券。mall-coupon 会把 preview 的校验全部重做一遍 ——
     * 两次调用之间券可能已被另一笔并发订单用掉。
     * <p>
     * <b>返回非 0 必须让下单失败</b>，不能忽略：忽略就是一张券抵扣两笔订单。
     */
    @PostMapping("/coupon/promotion/internal/use")
    R useCoupon(@RequestParam("historyId") Long historyId,
                @RequestParam("memberId") Long memberId,
                @RequestParam("amount") BigDecimal amount,
                @RequestParam("orderSn") String orderSn,
                @RequestHeader("X-Seckill-Internal-Token") String internalToken);

    /**
     * 退券补偿。mall-coupon 侧永远返回成功（幂等），
     * 所以调用方只需要防住"远程调用本身抛异常"。
     */
    @PostMapping("/coupon/promotion/internal/release")
    R releaseCoupon(@RequestParam("historyId") Long historyId,
                    @RequestParam("orderSn") String orderSn,
                    @RequestHeader("X-Seckill-Internal-Token") String internalToken);
}

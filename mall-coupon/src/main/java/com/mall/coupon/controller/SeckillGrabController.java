package com.mall.coupon.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.R;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.interceptor.CouponInterceptor;
import com.mall.coupon.service.SeckillGrabService;
import com.mall.coupon.service.SeckillLocalMessageService;
import com.mall.coupon.vo.SeckillGrabResultVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


/**
 * 秒杀抢购面向前台会员的接口，走 seckill.mall.com 域名（见 mall-gateway 的
 * mall_seckill_route）。跟 {@link SeckillSkuRelationController} 那批 renren 生成的
 * 后台管理 CRUD 接口是两条不同的路，这里才是真正的抢购业务入口。
 */
@RestController
@RequestMapping("coupon/seckill")
public class SeckillGrabController {

    public static final String INTERNAL_TOKEN_HEADER = "X-Seckill-Internal-Token";

    @Autowired
    private com.mall.coupon.config.SeckillBulkhead bulkhead;

    @Autowired
    private SeckillGrabService seckillGrabService;

    @Autowired
    private SeckillLocalMessageService seckillLocalMessageService;

    @Value("${mall.seckill.internal-token}")
    private String internalToken;

    /**
     * 商家在 admin 上架秒杀场次后调用，把库存预热进 Redis。这条路由和下面的
     * order-created 回调一样是"内部调用"接口，不走会员登录态，但网关那条
     * seckill.mall.com 路由是完全公开的，所以必须靠共享密钥挡住匿名请求——
     * 否则任何人都能反复重置库存/抢购名单，或伪造建单回调。
     */
    @PostMapping("/activate/{relationId}")
    public R activate(@PathVariable("relationId") Long relationId,
                       @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        boolean ok = seckillGrabService.activate(relationId);
        if (!ok) {
            return R.error(ErrorCode.SECKILL_NOT_ACTIVE);
        }
        return R.ok();
    }

    /**
     * 抢购。
     * <p>
     * 这个方法原来返回 Callable，走 Spring MVC 的异步 servlet 处理，为的是不让
     * grab() 内部等 RabbitMQ publisher confirm（最多 3 秒）的那段时间占住一个 Tomcat
     * 平台线程。阶段 8 开启虚拟线程之后这层机制不再需要：请求本身就跑在虚拟线程上，
     * 阻塞时会从载体线程卸载，等待不消耗稀缺资源。于是这里可以退回最朴素的同步写法。
     * <p>
     * 顺带消掉了旧写法里一个绕不过去的开销：因为返回类型是 Callable，
     * 即使是「未登录」这种一眼就能判掉的情况，Spring MVC 也照样要把它提交给异步执行器
     * 跑一遍。现在直接返回即可。
     * <p>
     * 外面套的 {@link SeckillBulkhead} 不是顺手加的：原来那个有界线程池同时在隐式
     * 限制在途并发（超过约 2200 就拒绝），虚拟线程把这个天花板拿掉了，必须显式补回来。
     * 详见 SeckillBulkhead 的类注释。
     */
    @PostMapping("/grab/{relationId}")
    public R grab(@PathVariable("relationId") Long relationId) {
        Long memberId = requireMemberId();
        if (memberId == null) {
            return NOT_LOGIN;
        }
        return bulkhead.call(
                () -> toResponse(seckillGrabService.grab(relationId, memberId, requireUsername())),
                () -> BUSY);
    }

    /**
     * 抢到但没有默认地址时，确认页选完地址后调用这个把订单真正建起来。
     * 同样从异步 Callable 退回同步写法，原因见 grab() 的注释。
     */
    @PostMapping("/message/{messageId}/address")
    public R submitAddress(@PathVariable("messageId") Long messageId, @RequestParam("addrId") Long addrId) {
        Long memberId = requireMemberId();
        if (memberId == null) {
            return NOT_LOGIN;
        }
        return bulkhead.call(
                () -> toResponse(seckillGrabService.submitAddress(messageId, memberId, addrId)),
                () -> BUSY);
    }

    /**
     * 前端轮询用：MQ 是异步的，订单号要等 mall-order 那边消费完才会回填。
     */
    @GetMapping("/message/{messageId}")
    public R messageStatus(@PathVariable("messageId") Long messageId) {
        Long memberId = requireMemberId();
        if (memberId == null) {
            return NOT_LOGIN;
        }
        SeckillLocalMessageEntity message = seckillLocalMessageService.getById(messageId);
        if (message == null || !message.getMemberId().equals(memberId)) {
            return R.error(ErrorCode.SECKILL_MESSAGE_INVALID);
        }
        return R.ok().put("status", message.getStatus()).put("orderSn", message.getOrderSn());
    }

    /**
     * mall-order 消费 MQ 建单成功后的内部回调，靠共享密钥而不是会员登录校验
     * （见上面 activate() 的说明——这条路由同样暴露在公网上）。
     */
    @PostMapping("/message/{messageId}/order-created")
    public R orderCreated(@PathVariable("messageId") Long messageId, @RequestParam("orderSn") String orderSn,
                           @RequestHeader(value = INTERNAL_TOKEN_HEADER, required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        seckillGrabService.handleOrderCreated(messageId, orderSn);
        return R.ok();
    }

    private static final R NOT_LOGIN = R.error(401, "请先登录");

    /**
     * 闸门拒绝时的返回。用 503 而不是 500：这不是服务出错，是主动限流，
     * 语义上属于「暂时无法处理」。前端据此提示「当前人数过多，请稍后再试」，
     * 监控上也能和真正的错误区分开 —— 混成 500 会让告警噪声淹没真实故障。
     */
    private static final R BUSY = R.error(503, "当前抢购人数过多，请稍后再试");

    private boolean requireInternalToken(String token) {
        return StringUtils.hasText(internalToken) && internalToken.equals(token);
    }

    private Long requireMemberId() {
        return CouponInterceptor.threadLocal.get().getUserId();
    }

    private String requireUsername() {
        return CouponInterceptor.threadLocal.get().getUsername();
    }

    private R toResponse(SeckillGrabResultVo vo) {
        if (!vo.isSuccess()) {
            return R.error(vo.getFailReason());
        }
        return R.ok()
                .put("hasDefaultAddress", vo.isHasDefaultAddress())
                .put("messageId", vo.getMessageId());
    }
}

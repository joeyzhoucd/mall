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

import java.util.concurrent.Callable;

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
     * 返回 Callable 让 Spring MVC 走异步 servlet 处理：seckillGrabService.grab() 内部
     * 要等 RabbitMQ publisher confirm（最多 3 秒），真正的等待丢给 CouponWebConfig
     * 里配的那个有界线程池去做，Tomcat 线程立刻还给容器去服务别的请求，不会被
     * 抢购高峰一波打满。这里没必要"提前"（这个方法的调用点）就先把 memberId/
     * username 手动取出来再闭包传进去——CouponWebConfig.seckillAsyncExecutor 挂了
     * UserContextTaskDecorator，登录态会自动跟着任务"跳"到线程池线程上，Callable
     * 内部跟同步代码一样直接读 CouponInterceptor.threadLocal 就行。
     * <p>
     * 注意：下面 requireMemberId()==null 这个提前判断只是让没登录的请求不用真的
     * 调一次 seckillGrabService（省一次业务逻辑），并不会省掉"提交给线程池"这一步
     * 本身——方法返回类型是 Callable，不管里面装的是什么，Spring MVC 都会照样把它
     * 交给异步执行器跑一遍，这个开销跳不过去，纯粹是异步 servlet 处理的机制决定的。
     */
    @PostMapping("/grab/{relationId}")
    public Callable<R> grab(@PathVariable("relationId") Long relationId) {
        if (requireMemberId() == null) {
            return () -> NOT_LOGIN;
        }
        return () -> {
            SeckillGrabResultVo vo = seckillGrabService.grab(relationId, requireMemberId(), requireUsername());
            return toResponse(vo);
        };
    }

    /**
     * 抢到但没有默认地址时，确认页选完地址后调用这个把订单真正建起来。
     * 同样走异步处理，原因见 grab() 上面的注释。
     */
    @PostMapping("/message/{messageId}/address")
    public Callable<R> submitAddress(@PathVariable("messageId") Long messageId, @RequestParam("addrId") Long addrId) {
        if (requireMemberId() == null) {
            return () -> NOT_LOGIN;
        }
        return () -> {
            SeckillGrabResultVo vo = seckillGrabService.submitAddress(messageId, requireMemberId(), addrId);
            return toResponse(vo);
        };
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

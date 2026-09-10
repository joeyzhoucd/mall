package com.mall.coupon.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.R;
import com.mall.coupon.config.CouponClaimBulkheadConfiguration;
import com.mall.coupon.config.SeckillBulkhead;
import com.mall.coupon.interceptor.CouponInterceptor;
import com.mall.coupon.service.CouponClaimService;
import com.mall.coupon.to.UserInfoTo;
import com.mall.coupon.vo.MemberCouponVo;
import com.mall.coupon.vo.PromotionCouponVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 优惠券的领取和使用接口。
 *
 * <h3>这个类里有【两个信任域】，别混</h3>
 * <ol>
 *   <li><b>会员接口</b>（{@code /list} {@code /receive} {@code /my}）——
 *       身份来自服务端会话（{@link CouponInterceptor} 从 Spring Session 读），
 *       <b>绝不采信任何客户端传来的 memberId</b>。走 seckill.mall.com。</li>
 *   <li><b>内部接口</b>（{@code /internal/*}）—— 调用方是 mall-order，
 *       它有自己的会员会话，所以 memberId 由它作为参数传进来。
 *       这些接口<b>必须靠共享密钥挡住匿名请求</b>：网关那条 seckill.mall.com
 *       路由是完全公开的，没有密钥的话任何人都能伪造"用券/退券"，
 *       把别人的券标记成已使用（拒绝服务），或者把已用的券退回来（重复抵扣）。</li>
 * </ol>
 *
 * <h3>为什么内部接口复用 {@code X-Seckill-Internal-Token}，而不是新加一个密钥</h3>
 * 它保护的是<b>同一个信任关系</b>：mall-order → mall-coupon。
 * 新加一个密钥的爆炸半径完全相同（泄露任一个都等于能伪造这条链路上的调用），
 * 但要多维护一份 Sealed Secret、多一处两边配置必须一致的地方 ——
 * 而"两边配置不一致"正是这类共享密钥最常见的故障，且表现为 403 而非明确报错。
 * 头名字带 seckill 字样确实不准确，但把名字改对需要同时改 mall-order 的配置和
 * 已经部署的 Sealed Secret，风险大于收益。这里显式复用
 * {@link SeckillGrabController#INTERNAL_TOKEN_HEADER} 常量，让共用关系在代码里可见，
 * 而不是各写一遍同样的字符串然后指望它们一直一样。
 *
 * <h3>【限流】这些接口和秒杀共用一个 50 rps 的全局预算</h3>
 * 网关的 {@code mall_seckill_route} 上挂着
 * {@code replenishRate: 50 / burstCapacity: 100} 的 RequestRateLimiter，
 * key 是全局的（{@code seckillGlobalKeyResolver}）。因为促销页挂在同一个域名下，
 * <b>领券会和秒杀抢同一份预算</b>。两个后果要知道：
 * <ul>
 *   <li>大促时领券洪峰会挤占秒杀的配额。要分开的话得给促销单独一个域名+路由。</li>
 *   <li><b>压测必须绕过网关</b>，否则量到的是限流器不是业务逻辑：
 *       {@code ./run.sh claim 200 30s SCRIPT=coupon-claim.js TARGET=http://mall-coupon:10000}</li>
 * </ul>
 */
@RestController
@RequestMapping("coupon/promotion")
public class CouponClaimController {

    @Autowired
    private CouponClaimService couponClaimService;

    /**
     * 领券的并发闸门。
     *
     * <p><b>限定符不能省</b>：这个服务里有两个 {@code SeckillBulkhead} 类型的 bean
     * （秒杀一个、领券一个），按类型注入会是歧义，编译通过但启动直接崩。
     * 见 {@link com.mall.coupon.config.CouponClaimBulkheadConfiguration}。
     */
    @Autowired
    @Qualifier(CouponClaimBulkheadConfiguration.BEAN)
    private SeckillBulkhead claimBulkhead;

    @Value("${mall.seckill.internal-token}")
    private String internalToken;

    // =====================================================================
    // 会员接口：身份来自服务端会话
    // =====================================================================

    /**
     * 促销页的券列表。匿名可访问 —— 没登录也该能看到有哪些活动，
     * 点"领取"时再要求登录。
     */
    @GetMapping("/list")
    public R list() {
        List<PromotionCouponVo> coupons = couponClaimService.promotions();
        return R.ok().put("coupons", coupons);
    }

    /**
     * 领券。
     *
     * <p><b>memberId 只从会话取</b>。做成 {@code @RequestParam Long memberId} 的话，
     * 任何人都能替别人领券把额度刷光（这个库里已经有过一次同类问题：
     * 收货地址接口原来由客户端自报 memberId，见
     * {@code MemberFeignService.saveAddress} 的注释）。
     *
     * <p>返回的 {@code code} 就是 {@link ErrorCode} 的码，每种失败一个 ——
     * 压测靠它区分"券发完了"(23002) 和"这个人领满了"(23003)。
     *
     * <h3>【外面套了并发闸门】过载时快速拒绝，而不是一起卡在连接池上</h3>
     * 2026-09-09 压测实测：没有闸门时 200 rps 会产生 2064 次 Hikari 连接获取超时、
     * 对应 2063 个 HTTP 500。池只有 5 个连接，而 600 个在途请求全都挤上去，
     * 每个等满 3 秒再一起失败。
     * <p>
     * 闸门把这个失败模式换成：容量之内正常处理，超出的立刻返回
     * {@code 23008 太忙}。三个好处：
     * <ul>
     *   <li>被拒的用户 <b>3 毫秒</b>就得到答复，而不是等 3 秒</li>
     *   <li>返回的是一个语义正确、<b>可重试</b>的业务码，不是 500</li>
     *   <li>拒绝次数有独立计数器，过载程度可观测</li>
     * </ul>
     *
     * <h3>身份检查放在闸门【外面】</h3>
     * 未登录的请求根本不消耗通行证 —— 它不碰数据库，没有理由占用为
     * 真实业务准备的并发额度。反过来把它放进去，一波匿名流量就能把闸门占满。
     */
    @PostMapping("/receive/{couponId}")
    public R receive(@PathVariable("couponId") Long couponId) {
        UserInfoTo user = CouponInterceptor.threadLocal.get();
        if (user == null || user.getUserId() == null) {
            return R.error(ErrorCode.COUPON_UNAUTHENTICATED);
        }
        return claimBulkhead.call(
                () -> {
                    CouponClaimService.ClaimResult result =
                            couponClaimService.receive(couponId, user.getUserId(), user.getUsername());
                    if (!result.ok()) {
                        return R.error(result.code());
                    }
                    return R.ok().put("historyId", result.historyId());
                },
                () -> R.error(ErrorCode.COUPON_TOO_BUSY));
    }

    /**
     * 我的优惠券。
     *
     * @param status null = 全部；0 = 可用；1 = 已使用；2 = 已过期
     */
    @GetMapping("/my")
    public R myCoupons(@RequestParam(value = "status", required = false) Integer status) {
        UserInfoTo user = CouponInterceptor.threadLocal.get();
        if (user == null || user.getUserId() == null) {
            return R.error(ErrorCode.COUPON_UNAUTHENTICATED);
        }
        List<MemberCouponVo> coupons = couponClaimService.myCoupons(user.getUserId(), status);
        return R.ok().put("coupons", coupons);
    }

    // =====================================================================
    // 内部接口：调用方 mall-order，必须带共享密钥
    // =====================================================================

    /**
     * 结算页可选的券。mall-order 渲染 orderConfirm.html 时调。
     *
     * <p>门槛过滤在 SQL 里做，所以返回的都是当前金额下真能用的。
     * 但<b>后端用券时仍然会再校验一遍</b> —— 客户端可以提交任意 historyId，
     * 不能假设它只会提交这个列表里的。
     */
    @GetMapping("/internal/usable")
    public R usable(@RequestParam("memberId") Long memberId,
                    @RequestParam("amount") BigDecimal amount,
                    @RequestHeader(value = SeckillGrabController.INTERNAL_TOKEN_HEADER,
                            required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        return R.ok().put("coupons", couponClaimService.usableForAmount(memberId, amount));
    }

    /**
     * 预览抵扣金额 —— 只读，不改任何状态，所以失败不需要补偿。
     * <p>
     * mall-order 用它算应付金额做价格校验。价格变动/库存不足这些常见失败
     * 都发生在券状态被改动之前，于是走不到退券那条脆弱的补偿路径。
     */
    @PostMapping("/internal/preview")
    public R preview(@RequestParam("historyId") Long historyId,
                     @RequestParam("memberId") Long memberId,
                     @RequestParam("amount") BigDecimal amount,
                     @RequestHeader(value = SeckillGrabController.INTERNAL_TOKEN_HEADER,
                             required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        CouponClaimService.UseResult result = couponClaimService.preview(historyId, memberId, amount);
        if (!result.ok()) {
            return R.error(result.code());
        }
        return R.ok().put("discount", result.discount());
    }

    /**
     * 用券。会把 preview 的校验全部重做一遍 —— 两次调用之间券可能已被
     * 另一笔并发订单用掉，或刚好过期。
     */
    @PostMapping("/internal/use")
    public R use(@RequestParam("historyId") Long historyId,
                 @RequestParam("memberId") Long memberId,
                 @RequestParam("amount") BigDecimal amount,
                 @RequestParam("orderSn") String orderSn,
                 @RequestParam(value = "orderId", required = false) Long orderId,
                 @RequestHeader(value = SeckillGrabController.INTERNAL_TOKEN_HEADER,
                         required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        CouponClaimService.UseResult result =
                couponClaimService.use(historyId, memberId, amount, orderSn, orderId);
        if (!result.ok()) {
            return R.error(result.code());
        }
        return R.ok().put("discount", result.discount());
    }

    /**
     * 退券补偿。
     *
     * <p><b>永远返回成功</b>，即使这次没退到（券已经不是这笔订单占着）。
     * 因为调用方是 mall-order 的 catch 块 —— 那里再抛异常会连累
     * 库存释放也发不出去。这个仓库刚好被这个模式坑过一次：
     * catch 里的补偿动作访问了一张缺失的表，异常穿透 catch，
     * 结果 {@code sendStockRelease} 永远没执行，锁定库存永久泄漏。
     * <p>
     * 幂等性由 {@code markUnusedByOrder} 的 WHERE 保证（带 order_sn + use_type=1），
     * 重复调用第二次什么都不会改。
     */
    @PostMapping("/internal/release")
    public R release(@RequestParam("historyId") Long historyId,
                     @RequestParam("orderSn") String orderSn,
                     @RequestHeader(value = SeckillGrabController.INTERNAL_TOKEN_HEADER,
                             required = false) String token) {
        if (!requireInternalToken(token)) {
            return R.error(ErrorCode.SECKILL_FORBIDDEN);
        }
        boolean released = couponClaimService.release(historyId, orderSn);
        return R.ok().put("released", released);
    }

    /**
     * 校验内部令牌。
     *
     * <p>{@code StringUtils.hasText(internalToken)} 这一半不能省：
     * 如果配置缺失导致 {@code internalToken} 是空串，
     * 那么 {@code "".equals(null)} 为 false 会挡住合法调用（还算安全），
     * 但 {@code "".equals("")} 为 true 会让<b>任何传空头的请求都通过</b>。
     * 和 {@code SeckillGrabController.requireInternalToken} 保持一致。
     */
    private boolean requireInternalToken(String token) {
        return StringUtils.hasText(internalToken) && internalToken.equals(token);
    }
}

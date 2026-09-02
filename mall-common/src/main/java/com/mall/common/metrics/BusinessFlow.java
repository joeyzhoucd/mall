package com.mall.common.metrics;

/**
 * 业务流名称与失败原因的常量集合。
 *
 * <h3>为什么用常量而不是随手写字符串</h3>
 * 这些字符串会变成 Prometheus 的标签值，进而被告警规则和面板引用。
 * 写成字面量的话，改一个名字不会有任何编译错误，而告警规则和面板会静默地
 * 再也匹配不到数据 —— 这个项目已经在别处栽过好几次同样形状的跟头
 * （Alloy 的 trace_id 正则、失效的 logging.pattern.level）。
 * <p>
 * 集中在这里还有一个作用：<b>让「有哪些失败原因」这件事可以一眼看完</b>，
 * 而不用去翻三个服务的代码。基数护栏也就有了参照。
 */
public final class BusinessFlow {

    private BusinessFlow() {
    }

    // ------------------------------------------------------------------ 业务流

    /** 提交订单（mall-order）。 */
    public static final String ORDER_SUBMIT = "order.submit";

    /** 秒杀抢购（mall-coupon）。 */
    public static final String SECKILL_GRAB = "seckill.grab";

    /** 下单时锁定库存（mall-ware）。 */
    public static final String STOCK_LOCK = "stock.lock";

    /** 库存解锁（订单关闭后归还）。 */
    public static final String STOCK_UNLOCK = "stock.unlock";

    /** 库存扣减（订单支付后真实扣掉）。 */
    public static final String STOCK_DEDUCT = "stock.deduct";

    // ------------------------------------------------ 下单失败原因（比返回码更细）

    /** 未登录或拿不到用户上下文。 */
    public static final String REASON_UNAUTHENTICATED = "unauthenticated";

    /**
     * 令牌校验未通过 —— 绝大多数是用户双击重复提交。
     * <p>
     * 刻意和下面的 {@link #REASON_PERSIST_FAILED} 分开：两者在 API 上都是返回码 1，
     * 但一个是无害的用户行为、一个是真故障。混在一起算「下单失败率」，
     * 会让这个指标被双击噪声污染到没法定阈值。
     */
    public static final String REASON_DUPLICATE_SUBMIT = "duplicate_submit";

    /** 收货地址无效或不属于该用户。 */
    public static final String REASON_ADDRESS_INVALID = "address_invalid";

    /** 前端提交的价格和后端重算的差超过 1 分钱。 */
    public static final String REASON_PRICE_CHANGED = "price_changed";

    /** 调 mall-ware 锁库存失败（库存不足，或那个服务本身不可用）。 */
    public static final String REASON_STOCK_LOCK_FAILED = "stock_lock_failed";

    /** 落库或发 MQ 抛异常 —— 这条才是真正需要有人去看的。 */
    public static final String REASON_PERSIST_FAILED = "persist_failed";

    // -------------------------------------------------------- 库存相关失败原因

    /**
     * CAS 没抢到处理权 —— 状态已经不是 LOCKED，说明另一个执行流已经处理完了。
     * <p>
     * <b>这不是故障</b>，是并发下的正常结果（MQ 监听器和重试任务会撞，
     * 两个副本也会撞）。单独记一个原因是为了把它和真失败区分开：
     * 它的量应该和并发度相关，突然变多说明重复投递变多了，值得看一眼。
     * <p>
     * 注意：{@code StockAtomicOps.unlock/deduct} 的「参数不合法」分支也返回 false，
     * 从调用方分不出来，所以也会落到这个 reason 上。那个分支已经单独打了
     * {@code log.warn}（原来是静默 return），真出现时靠日志告警发现，不靠指标区分 ——
     * 与其为一个实践上不该发生的情况多开一个标签值，不如让日志说话。
     */
    public static final String REASON_CAS_LOST = "cas_lost";

    /** 库存不足。 */
    public static final String REASON_INSUFFICIENT = "insufficient";
}

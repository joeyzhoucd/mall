package com.mall.common.metrics;

import com.mall.common.constant.ErrorCode;

import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    /**
     * 秒杀抢到后补选收货地址（mall-coupon，{@code submitAddress}）。没有默认地址的用户
     * 抢购和建单之间隔着这一步：MQ 在这里才发。2026-10-12 之前这一步不计数，
     * 它的 {@code seckill_mq_failed} 只能在日志里看到。幂等重放（消息已发过）不计数，
     * 否则成功数会多于实际建单数。
     */
    public static final String SECKILL_ADDRESS = "seckill.address";

    /** 下单时锁定库存（mall-ware）。 */
    public static final String STOCK_LOCK = "stock.lock";

    /** 库存解锁（订单关闭后归还）。 */
    public static final String STOCK_UNLOCK = "stock.unlock";

    /** 库存扣减（订单支付后真实扣掉）。 */
    public static final String STOCK_DEDUCT = "stock.deduct";

    /**
     * 领券。
     * <p>
     * 【为什么领券也要打点】领券压测的正确性判据是<b>计数</b>而不是错误：
     * 并发 200 抢 10 张，超发时表现为 11 个成功 —— 没有任何异常、没有任何日志。
     * 只有把每种结果分开计数，才能在 Grafana 上直接读出
     * "成功数是否恰好等于发行量"。
     */
    public static final String COUPON_RECEIVE = "coupon.receive";

    /** 用券（下单时抵扣）。 */
    public static final String COUPON_USE = "coupon.use";

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

    /**
     * 调 mall-ware 锁库存时抛异常（熔断打开、读超时）—— 和库存不足的
     * {@link #REASON_STOCK_LOCK_FAILED} 分开：这个是依赖不可用。
     * 原来定义在 mall-order 的 OrderServiceImpl 里，2026-10-12 挪过来进清单，值不变。
     */
    public static final String REASON_STOCK_LOCK_UNAVAILABLE = "stock_lock_unavailable";

    /**
     * 下单闸门在过载时主动拒绝（设计好的降级，告警里被剔除）。
     * 原来定义在 mall-order 的 SubmitGate 里，2026-10-12 挪过来进清单，值不变。
     */
    public static final String REASON_GATE_BUSY = "gate_busy";

    /** 落库或发 MQ 抛异常 —— 这条才是真正需要有人去看的。 */
    public static final String REASON_PERSIST_FAILED = "persist_failed";

    /**
     * 下单时用券失败（券已被并发订单用掉、已过期、不满门槛、不属于此人）。
     * <p>
     * 和 {@link #REASON_PERSIST_FAILED} 分开记：用券失败绝大多数是用户
     * 停在结算页太久（券过期）或在两个标签页各下一单（券被抢先用掉），
     * 属于正常业务拒绝；混进"落库失败"会让那个指标被无害噪声污染到没法定阈值。
     * 这和当初把 duplicate_submit 从 persist_failed 里拆出来是同一个理由。
     */
    public static final String REASON_COUPON_INVALID = "coupon_invalid";

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

    // ------------------------------------------------------- 清单：流 -> 失败原因

    /**
     * 由 {@link ErrorCode} 派生的失败原因：枚举名转小写。秒杀、领券、用券都按这个规则出 reason，
     * 统一走这里，清单和调用方才不会各写各的。
     */
    public static String reasonOf(ErrorCode code) {
        return code.name().toLowerCase(Locale.ROOT);
    }

    /**
     * 每条业务流<b>所有可能的</b>失败原因（不含成功）。启动时按这张表给本服务负责的流
     * 预注册 0 值计数器（{@link BusinessMetrics#preRegister}）。
     *
     * <h3>为什么需要预注册（2026-10-12）</h3>
     * Micrometer 的 Counter 在第一次 increment 时才注册，零流量时整个序列<b>不存在</b>（不是 0）：
     * 面板显示 No data，「这条流的指标消失了」也没法用 absent() 告警 —— 分不清是没流量还是埋点坏了。
     *
     * <h3>漏登记会怎样</h3>
     * 不会丢数据：record 照常计数，只是那个组合不会被预注册，并打一条 warn。
     * CI 的 {@code check-business-reasons.js} 会拦住「代码里用了、表里没有」的原因。
     * ErrorCode 派生的原因写成 {@code reasonOf(ErrorCode.X)}，枚举改名时这里编译失败。
     */
    public static final Map<String, List<String>> FAILURE_REASONS = Map.of(
            ORDER_SUBMIT, List.of(
                    REASON_UNAUTHENTICATED, REASON_DUPLICATE_SUBMIT, REASON_ADDRESS_INVALID,
                    REASON_COUPON_INVALID, REASON_PRICE_CHANGED, REASON_STOCK_LOCK_FAILED,
                    REASON_STOCK_LOCK_UNAVAILABLE, REASON_PERSIST_FAILED, REASON_GATE_BUSY),
            SECKILL_GRAB, List.of(
                    reasonOf(ErrorCode.SECKILL_SOLD_OUT), reasonOf(ErrorCode.SECKILL_NOT_ACTIVE),
                    reasonOf(ErrorCode.SECKILL_ALREADY_GRABBED), reasonOf(ErrorCode.SECKILL_SYSTEM_ERROR),
                    reasonOf(ErrorCode.SECKILL_MQ_FAILED)),
            SECKILL_ADDRESS, List.of(
                    reasonOf(ErrorCode.SECKILL_MESSAGE_INVALID), reasonOf(ErrorCode.SECKILL_MQ_FAILED)),
            COUPON_RECEIVE, List.of(
                    reasonOf(ErrorCode.COUPON_UNAUTHENTICATED), reasonOf(ErrorCode.COUPON_NOT_FOUND),
                    reasonOf(ErrorCode.COUPON_NOT_PUBLISHED), reasonOf(ErrorCode.COUPON_RECEIVE_WINDOW_CLOSED),
                    reasonOf(ErrorCode.COUPON_MEMBER_LEVEL_NOT_MATCH), reasonOf(ErrorCode.COUPON_SOLD_OUT),
                    reasonOf(ErrorCode.COUPON_PER_LIMIT_EXCEEDED)),
            COUPON_USE, List.of(
                    reasonOf(ErrorCode.COUPON_NOT_OWNED), reasonOf(ErrorCode.COUPON_ALREADY_USED),
                    reasonOf(ErrorCode.COUPON_EXPIRED), reasonOf(ErrorCode.COUPON_SCOPE_NOT_MATCH),
                    reasonOf(ErrorCode.COUPON_THRESHOLD_NOT_MET)),
            STOCK_LOCK, List.of(REASON_INSUFFICIENT, REASON_PERSIST_FAILED),
            STOCK_UNLOCK, List.of(REASON_CAS_LOST),
            STOCK_DEDUCT, List.of(REASON_CAS_LOST));
}

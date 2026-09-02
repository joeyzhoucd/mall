package com.mall.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务结果计数。所有业务指标统一从这里出，指标名固定为 {@code mall.business.outcome}。
 *
 * <h3>为什么需要业务指标</h3>
 * 在这个类出现之前，全项目 208 个指标里<b>没有一个业务指标</b>——最接近的
 * {@code seckill_bulkhead_*} 其实是韧性指标（隔离舱容量）。
 * 后果很具体：能回答「mall-order 的 HTTP 错误率是多少」，
 * <b>回答不了「过去一小时成功下了多少单」</b>。
 * 如果某个分支静默地把订单丢了（比如 MQ 发出去了、消费端 catch 掉了），
 * 所有基础设施指标都是绿的，告警一条都不响。
 *
 * <h3>维度可以比 API 返回码更细，而且应该更细</h3>
 * 例子：{@code submitOrder} 的返回码 1 混了三种原因——未登录、令牌校验失败
 * （用户双击重复提交）、保存异常。对监控来说这三者意义天差地别：
 * 重复提交是无害的用户行为，保存异常是真故障。
 * 直接照抄返回码会让「下单失败率」被双击噪声污染，所以这里用更细的 reason。
 *
 * <h3>基数护栏（这个类唯一的「聪明」之处，也是它存在的理由）</h3>
 * Prometheus 的序列数 = 各标签取值的笛卡尔积。业务埋点最容易犯的错是
 * 把异常消息、订单号、SKU ID 之类当成标签传进来——那会让序列数无上限增长，
 * 把 TSDB 撑爆（本集群只有 2Gi）。而且这种事故是<b>渐进的</b>：
 * 上线时一切正常，跑几天之后 Prometheus 开始 OOM。
 * <p>
 * 所以这里对每条业务流的 (result, reason) 组合数设了上限，超出的一律归到
 * {@code _overflow} 并打 warn——宁可丢掉细节，也不能让观测系统本身成为故障源。
 */
public class BusinessMetrics {

    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);

    /** 统一的指标名。Prometheus 里是 {@code mall_business_outcome_total}。 */
    public static final String METRIC_NAME = "mall.business.outcome";

    /** 每条业务流允许的 (result, reason) 组合数上限。 */
    static final int MAX_COMBINATIONS_PER_FLOW = 32;

    /** 超限之后统一用这个 reason，保证序列数有天花板。 */
    static final String OVERFLOW_REASON = "_overflow";

    /** reason 为空时的占位，避免出现空标签值（Prometheus 里空值和缺失难区分）。 */
    static final String NO_REASON = "none";

    private final MeterRegistry registry;

    /** flow -> 已见过的 "result|reason" 组合。只增不减，天花板由 MAX_COMBINATIONS_PER_FLOW 保证。 */
    private final ConcurrentHashMap<String, Set<String>> seenCombinations = new ConcurrentHashMap<>();

    public BusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 记一次业务结果。
     *
     * @param flow   业务流名，取值必须是<b>编译期固定</b>的常量（见 {@link BusinessFlow}）
     * @param result {@code success} 或 {@code failure}——只有这两个值，方便算成功率
     * @param reason 失败原因；成功时传 null。必须来自有界集合（枚举名、常量），
     *               <b>绝不能传异常消息或任何带 ID 的字符串</b>
     */
    public void record(String flow, String result, String reason) {
        String safeReason = (reason == null || reason.isBlank()) ? NO_REASON : reason;
        String combo = result + "|" + safeReason;

        Set<String> seen = seenCombinations.computeIfAbsent(flow, k -> ConcurrentHashMap.newKeySet());
        if (!seen.contains(combo)) {
            if (seen.size() >= MAX_COMBINATIONS_PER_FLOW) {
                // 已经到顶了。不再接受新组合，归并到 _overflow。
                log.warn("业务指标基数超限：flow={} 已有 {} 种 (result,reason) 组合，"
                                + "新组合 {} 被归并到 {}。这通常意味着有人把异常消息或 ID 当 reason 传进来了 ——"
                                + "reason 必须来自有界集合。",
                        flow, seen.size(), combo, OVERFLOW_REASON);
                counter(flow, result, OVERFLOW_REASON).increment();
                return;
            }
            seen.add(combo);
        }
        counter(flow, result, safeReason).increment();
    }

    /** 成功。 */
    public void success(String flow) {
        record(flow, "success", null);
    }

    /** 失败，带原因。 */
    public void failure(String flow, String reason) {
        record(flow, "failure", reason);
    }

    private Counter counter(String flow, String result, String reason) {
        // Micrometer 按 (name + tags) 缓存 meter，重复 register 返回同一个实例，
        // 所以这里不用自己缓存 Counter，只需要缓存「见过哪些组合」用于限流。
        return Counter.builder(METRIC_NAME)
                .description("业务动作的结果计数。用 result 算成功率，用 reason 定位失败原因。")
                .tag("flow", flow)
                .tag("result", result)
                .tag("reason", reason)
                .register(registry);
    }

    /** 仅供测试观察护栏状态。 */
    int seenCombinationCount(String flow) {
        Set<String> seen = seenCombinations.get(flow);
        return seen == null ? 0 : seen.size();
    }
}

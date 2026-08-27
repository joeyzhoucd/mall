package com.mall.coupon.config;

import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.Gradient2Limit;
import com.netflix.concurrency.limits.limiter.SimpleLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 自适应并发闸门：按观测到的延迟动态调整并发上限。
 *
 * <h3>为什么需要它 —— 固定值在这里必然是错的</h3>
 * 压测实测（见 {@link StaticSeckillBulkhead} 的注释）：同样 50 rps，
 * <b>充分预热的服务 p95 82ms 从容处理，刚启动的同一个服务 p95 约 10 秒并被存活探针
 * 杀掉</b>。也就是说真实容量在冷热之间差了一个数量级。
 * <p>
 * 静态信号量只能取一个折中值（当前 32）：对冷态偏大 —— 仍会放进来一批 CPU 喂不动的
 * 请求；对热态偏小 —— 系统本来还能多服务一些却被拒了。
 * <b>这不是「32 选得不够好」，而是任何固定值都无法同时满足两种状态。</b>
 * 所以形状上对症的做法是让上限自己跟着系统状态走。
 *
 * <h3>为什么选 Gradient2 而不是 AIMD</h3>
 * 关键在于<b>我们的失败模式里没有错误信号</b>：冷启动过载时接口不报错、不超时（服务端
 * 视角），只是延迟从 82ms 涨到 10 秒。
 * <ul>
 *   <li>AIMD 依赖「超时/丢弃」这样的离散失败信号来做乘性减小。等到客户端超时被服务端
 *       感知到，积压已经形成了 —— 信号来得太晚。</li>
 *   <li>Gradient2 比较<b>短期 RTT 与长期 RTT 的比值</b>：延迟一开始变差就收缩限额，
 *       延迟回落就放开。它测的正是我们唯一有的那个信号。</li>
 * </ul>
 * 这也是 Netflix 在他们的实践里把 gradient 系列作为默认推荐的原因：
 * 服务降级通常先表现为变慢，而不是先表现为报错。
 *
 * <h3>参数怎么定的</h3>
 * <ul>
 *   <li>{@code initialLimit=32}：直接沿用静态实现校准出来的值。刚启动时算法还没有
 *       足够样本，这时候的初值就是它的行为，用一个有实测依据的数比用库的默认值好。</li>
 *   <li>{@code minLimit=4}：下限。留一个正数是必要的 —— 降到 0 会让服务完全不可用，
 *       而那时连"探测系统是否恢复"的样本都拿不到，算法会永远卡在关闭状态。</li>
 *   <li>{@code maxConcurrency=200}：上限，就是改造前那个拍脑袋的值。实测证明它作为
 *       <b>固定</b>值太大，但作为<b>上限</b>是合适的 —— 热态实测能吃下 70 rps × 82ms
 *       ≈ 6 个在途，200 留了足够余量，同时防止算法在一段异常快的样本后无限放开。</li>
 *   <li>{@code rttTolerance=1.5}：短期 RTT 超过长期 RTT 的 1.5 倍才开始收缩。
 *       调小会更敏感但容易被抖动带偏，调大反应更慢。1.5 是库的建议起点，
 *       <b>这个值还没有实测校准</b>。</li>
 * </ul>
 *
 * <h3>已知的局限，用之前必须知道</h3>
 * <b>1) 秒杀的延迟分布是双峰的，会带偏 RTT 测量。</b>
 * 绝大多数请求走"本地已知售罄/已抢过"的快速路径（约 0ms），少数中奖的走完整重路径
 * （约 100ms）。算法看到的平均 RTT 由廉价请求主导，于是会把限额放得偏大。
 * <p>
 * 这里<b>刻意仍然把全部请求都当成有效样本</b>（都调 {@code onSuccess()}），理由是：
 * 闸门要限的是"在途工作量"，如果流量确实以廉价请求为主，那更大的限额本身是对的；
 * 而当结构突变（冷启动时所有请求都变慢）时，RTT 会整体上升、算法会收缩 ——
 * 那正是我们最需要它起作用的场景。
 * <p>
 * 但这条要盯着指标验证，而不是当成已经想清楚了。观察方法：把
 * {@code seckill_bulkhead_capacity}（自适应模式下就是当前限额）和抢购成功率、p95
 * 画在一起。如果限额在流量结构变化时明显跟错方向，就需要给快速路径改用
 * {@code onIgnore()}（那要让 action 把"我走的是哪条路径"回传上来，会侵入接口）。
 * <p>
 * <b>2) 引入了一个会自己变的量，行为比固定值难预测。</b>
 * 出问题时排查要多看一条曲线。所以静态实现保留着，可以一键切回
 * （{@code mall.seckill.bulkhead.mode=static}）。
 * <p>
 * <b>3) 库的维护很轻。</b>concurrency-limits 0.5.4 是最新版，Netflix 已经不太更新了。
 * 它零业务依赖、代码量小，风险可控，但不要指望有上游修复。
 * <p>
 * <b>4) 机制已验证可用，但相对静态实现【没有测出优势】。</b>
 * 2026-08-27 用 mall-deploy/loadtest/bulkhead-ab.sh 做了交替 A/B（各 2 对）：
 * <pre>
 * 热态 120 rps          抢中   闸门拒绝  实际到达  p95(抢中)  限额
 *   对1 static          2108    1107     119.7     990ms     32(固定)
 *   对1 adaptive        2064    1219     119.1    1114ms     收缩到 9
 *   对2 static           664     420      62.8*    951ms     32(固定)
 *   对2 adaptive        1937    1307     118.6    1351ms     收缩到 13
 *
 * 冷启动 50 rps
 *   对1 static            78     144      31.2*   3294ms     32(固定)
 *   对1 adaptive         634     707      44.8    8263ms     约 32
 *   对2 static           967     502      49.0    3599ms     32(固定)
 *   对2 adaptive        1013     467      49.3    3454ms     涨到 74
 * </pre>
 * (*) 这两轮按压测脚本自己的有效性判据<b>属于无效数据</b>：丢弃率 38%/48%，
 * 到达速率远低于预定，是 <b>k6 客户端跟不上</b>而不是服务端表现。
 * 而恰恰是这两轮显示出最大的"差异" —— 只看数字不看有效性判据，
 * 会得出「自适应吞吐高 3 倍」这种完全错误的结论。
 * <p>
 * <b>确定的结论</b>：限额在 120 rps 持续压力下收缩到 9–13、在延迟低时涨到 61–74，
 * 说明算法是活的且方向正确。<b>不确定的</b>：有效的那几轮全是平手
 * （2108 对 2064、967 对 1013、3599ms 对 3454ms），<b>看不出吞吐或延迟优势</b>。
 * <p>
 * 一个值得记的观察（只有一次，不是结论）：自适应用<b>限额 10</b> 达到了静态
 * <b>限额 32</b> 的吞吐。这暗示 32 是超配的 —— 多出来的在途容量买到的是排队而不是吞吐。
 * <p>
 * <b>要真正settle 这件事还缺什么</b>：
 * <ul>
 *   <li>压测客户端要能撑住预定速率（K6_CPU 调到 4 以上、maxVUs 加大），
 *       否则一半的轮次是无效数据；</li>
 *   <li>更多重复次数 —— 这套环境同配置两轮 p95 能差一个数量级；</li>
 *   <li>换一个能体现"保护能力"的判据。吞吐和 p95 衡量的是"跑得多快"，
 *       而闸门的价值在"过载时不把自己搞死" —— 更该看的是 pod 是否存活、
 *       积压是否在流量停止后很快排空。</li>
 * </ul>
 * 所以默认仍是 adaptive（机制正确、且找到了更低的工作点），但<b>这是基于原理的选择，
 * 不是被实测证明的优势</b>。切回静态：{@code mall.seckill.bulkhead.mode=static}。
 */
public class AdaptiveSeckillBulkhead implements SeckillBulkhead {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveSeckillBulkhead.class);

    private final Limiter<Void> limiter;
    private final Limit limit;
    private final LongAdder rejected = new LongAdder();

    /**
     * 在途计数自己维护：SimpleLimiter 没有公开在途数，而
     * {@code availablePermits()}（上限减在途）是判断"闸门是不是成了瓶颈"的关键指标，
     * 不能少。
     */
    private final AtomicInteger inFlight = new AtomicInteger();

    public AdaptiveSeckillBulkhead(int initialLimit, int minLimit, int maxConcurrency, double rttTolerance) {
        this(Gradient2Limit.newBuilder()
                .initialLimit(initialLimit)
                .minLimit(minLimit)
                .maxConcurrency(maxConcurrency)
                .rttTolerance(rttTolerance)
                .build());
    }

    /**
     * 接受任意 {@link Limit} 的构造，给测试用。
     * <p>
     * 抽出这个构造不只是为了可测：Gradient2 的限额变化取决于 RTT 窗口，
     * 在单元测试里没法确定性地触发。想验证「限额变了、闸门行为就跟着变」这条
     * 契约，只能注入一个可以直接设值的 Limit（库自带 SettableLimit）。
     * 否则那条测试就只能靠制造延迟去撞算法，成为一条偶发失败的噪声用例 ——
     * 那比没有测试更糟。
     */
    AdaptiveSeckillBulkhead(Limit limit) {
        this.limit = limit;
        this.limiter = SimpleLimiter.<Void>newBuilder()
                .named("seckill")
                .limit(this.limit)
                .build();

        // 限额变化打日志：这是自适应实现最重要的可观测信号，指标是采样的、
        // 短暂的收缩可能整段被漏掉，而日志不会。
        this.limit.notifyOnChange(newLimit ->
                log.info("秒杀闸门限额调整为 {}（自适应，Gradient2）", newLimit));
    }

    @Override
    public <T> T call(Supplier<T> action, Supplier<T> rejectedValue) {
        Optional<Limiter.Listener> listener = limiter.acquire(null);
        if (listener.isEmpty()) {
            rejected.increment();
            return rejectedValue.get();
        }
        inFlight.incrementAndGet();
        boolean businessFailed = false;
        try {
            return action.get();
        } catch (RuntimeException | Error e) {
            // 抛异常说明这次请求确实失败了，用 onDropped 让算法收缩 ——
            // 这和"业务上没抢到"是两件事：后者是正常的快速响应，仍算成功样本。
            businessFailed = true;
            throw e;
        } finally {
            inFlight.decrementAndGet();
            // 必须在 finally 里归还。漏归还的后果和静态实现一样是不可逆的：
            // 在途计数只增不减，限额再大也会被占满。
            if (businessFailed) {
                listener.get().onDropped();
            } else {
                listener.get().onSuccess();
            }
        }
    }

    @Override
    public int capacity() {
        return limit.getLimit();
    }

    @Override
    public int availablePermits() {
        return Math.max(0, limit.getLimit() - inFlight.get());
    }

    @Override
    public long rejectedCount() {
        return rejected.sum();
    }
}

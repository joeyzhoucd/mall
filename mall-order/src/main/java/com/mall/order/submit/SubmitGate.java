package com.mall.order.submit;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * 下单入口的并发闸门：进得来就快速成功，进不来就立即拒绝，没有「在连接池上排队 3 秒再 500」。
 *
 * <h3>为什么需要它</h3>
 * {@code OrderServiceImpl.submitOrder} 整个方法是一个事务，事务一开始就拿走一条连接，
 * 一直占到扣券 / 锁库存这些 Feign 调用都回来。每个 pod 的池只有 5 条（config-repo，
 * 受 MySQL max_connections=151 约束不能随便加）。2026-09-29 实测并发 64：两个 pod 3 分钟
 * 153 次 {@code Connection is not available}，成功率 69%，失败的全是等满 connection-timeout(3s) 的 500。
 *
 * <h3>为什么不直接用 mall-coupon 的 StaticSeckillBulkhead</h3>
 * 那个在 mall-coupon 里，挪进 mall-common 要重建全部服务；而且它的容量是构造时定死的，
 * 校准要一个值一个值地试。这里的上限可以运行时改（{@link #setLimit}，由内部接口调用），
 * 校准和事故时调参都不用重启。两者合并是另一件事。
 *
 * <h3>为什么不是 Semaphore</h3>
 * Semaphore 缩容要 reducePermits、扩容要 release，在途请求还没归还时两者交错很容易算错。
 * 这里只维护「在途数」和「上限」两个量，进门时比较，上限随时改、立即生效：
 * 调小之后已在途的照常完成，只是新的进不来，直到在途降到新上限以下。
 */
public class SubmitGate {

    /**
     * 业务指标里的失败原因（mall_business_outcome_total{flow="order.submit",reason="gate_busy"}）。
     * 放这里而不是 mall-common 的 BusinessFlow：改 mall-common 要重建全部服务。
     * 「下单成功率偏低」告警把它从分母里剔掉 —— 过载时主动拒绝是设计好的降级，不是故障；
     * 它单独由「下单闸门正在拒绝」告警盯。
     */
    public static final String REASON_BUSY = "gate_busy";
    /** 被拒的响应上带这个头，压测脚本据此把「闸门拒绝」和其他回结算页的失败分开数；对用户不可见 */
    public static final String DEGRADED_HEADER = "X-Mall-Degraded";
    public static final String DEGRADED_VALUE = "order-busy";
    /** JSON 入口（/order/submit）被闸门拒绝时的结果码；表单入口走回显，不用它 */
    public static final int BUSY_CODE = 7;

    private final AtomicInteger inFlight = new AtomicInteger();
    private final LongAdder rejected = new LongAdder();
    private volatile int limit;

    public SubmitGate(int limit) {
        setLimit(limit);
    }

    /** 抢一个名额；成功必须配对调用 {@link #exit()}（放在 finally 里） */
    public boolean tryEnter() {
        while (true) {
            int now = inFlight.get();
            if (now >= limit) {
                rejected.increment();
                return false;
            }
            if (inFlight.compareAndSet(now, now + 1)) {
                return true;
            }
        }
    }

    public void exit() {
        inFlight.decrementAndGet();
    }

    public void setLimit(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("下单闸门上限应在 1~1000：" + limit);
        }
        this.limit = limit;
    }

    public int limit() {
        return limit;
    }

    public int inFlight() {
        return inFlight.get();
    }

    public long rejectedCount() {
        return rejected.sum();
    }
}

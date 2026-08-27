package com.mall.coupon.config;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 固定容量的闸门实现：一个信号量，拿不到就立刻拒绝。
 *
 * <h3>默认 32 是 2026-08-27 压测校准出来的</h3>
 * 此前是拍脑袋的 200。压测条件：mall-coupon 限 500m CPU / 768Mi，100 个会员 × 30 个
 * 活动提供唯一「会员×活动」组合，k6 在集群内直压 /coupon/seckill/grab。
 * 脚本见 mall-deploy/loadtest/seckill-grab.js。
 *
 * <h4>实测出来最重要的一件事：瓶颈不是速率，是冷启动</h4>
 * 充分预热后 mall-coupon 在 500m CPU 下轻松吃下 70 rps（2100/2100 全部成功，
 * p95 82ms，闸门零拒绝）。但<b>刚启动的同一个服务，50 rps 就会把自己搞死</b>：
 * p95 约 10 秒、大量 15 秒客户端超时、CPU 100%，最后连存活探针都响应不过来，
 * 被 K8s SIGKILL。复现过两次。
 * <p>
 * 原因是冷启动时几件事叠在一起：JVM 还在解释执行（JIT 没编译完）、
 * Hikari 连接池 / RabbitMQ 连接 / Redis 连接池 / Feign 的 Consul 与负载均衡缓存
 * <b>全都是第一个请求才惰性初始化</b>，而 500m CPU 上 JVM 只看到 1 核、
 * GC 退化成 SerialGC。这些代价在空闲时无所谓，一上量就追不上。
 *
 * <h4>为什么 200 是个放大器而不是保护</h4>
 * 闸门的作用应该是「超过下游能力就立刻拒绝」。200 太大，实际效果是把 200 个冷 JVM
 * 根本喂不动的请求放进来一起排队，CPU 被彻底埋掉。更糟的是<b>客户端超时并不会让
 * 服务端停止工作</b>：压测只跑 30 秒，但积压让 pod 在两三分钟后才被探针杀掉 ——
 * 一次 30 秒的流量尖峰变成了几分钟的宕机。
 * <p>
 * 32 是按 Little's law 反推的：热态 70 rps × 82ms ≈ 6 个在途请求，留约 5 倍余量。
 * 两个方向都实测验证过：
 * <ul>
 *   <li>冷态 50 rps：p95 从 9956ms 降到 3213ms，零客户端超时，
 *       <b>pod 存活</b>（capacity=200 时同样负载下被 SIGKILL）；</li>
 *   <li>热态 70 rps：<b>零拒绝</b>、p95 82ms —— 说明 32 不会误伤系统本来能服务的请求。</li>
 * </ul>
 * 服务端的 rejected 计数器和 k6 客户端的统计完全一致（都是 465），互相印证。
 *
 * <h3>这个值的根本局限（也是为什么有了 {@link AdaptiveSeckillBulkhead}）</h3>
 * 冷态和热态的真实容量差了一个数量级，而这是个<b>静态</b>值，所以 32 本质上是
 * 两者之间的折中 —— 对冷态偏大、对热态偏小，两边都不最优。
 * 这不是「32 选得不够好」，是<b>任何固定值在这种情况下都是错的</b>。
 * <p>
 * 调大它不会让下游变快，只会让排队的位置从这里挪到连接池里。
 */
public class StaticSeckillBulkhead implements SeckillBulkhead {

    private final Semaphore permits;
    private final int capacity;

    /** 被挡下的请求数。用 LongAdder 而不是 AtomicLong：高竞争下写入快得多，而读取精度要求不高。 */
    private final LongAdder rejected = new LongAdder();

    public StaticSeckillBulkhead(int capacity) {
        this.capacity = capacity;
        // 非公平信号量：公平模式要维护等待队列、吞吐明显更低，而这里本来就是
        // tryAcquire 立即返回、不存在"等待很久拿不到"的饥饿问题，公平性没有意义。
        this.permits = new Semaphore(capacity, false);
    }

    @Override
    public <T> T call(Supplier<T> action, Supplier<T> rejectedValue) {
        if (!permits.tryAcquire()) {
            this.rejected.increment();
            return rejectedValue.get();
        }
        try {
            return action.get();
        } finally {
            // 必须在 finally 里释放。漏释放的后果是许可只减不增，闸门会越关越小，
            // 最终所有请求都被拒绝 —— 而且是不可逆的，只能重启 pod。
            permits.release();
        }
    }

    @Override
    public int availablePermits() {
        return permits.availablePermits();
    }

    @Override
    public int capacity() {
        return capacity;
    }

    @Override
    public long rejectedCount() {
        return rejected.sum();
    }
}

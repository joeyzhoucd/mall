package com.mall.coupon.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * 秒杀接口的并发闸门（bulkhead）。
 *
 * <h3>为什么在换成虚拟线程之后【必须】补上这个东西</h3>
 * 改造之前，秒杀接口靠 Callable + 一个有界线程池（core 50 / max 200 / queue 2000）做异步
 * servlet 处理。那个线程池除了"别占着 Tomcat 线程等 MQ 确认"之外，还在【隐式地承担限流】：
 * 在途请求超过 2200 个，线程池就开始拒绝任务，多余的流量被干脆利落地挡在业务逻辑之外。
 * <p>
 * 虚拟线程把这个天花板拿掉了 —— 阻塞变得极廉价，Tomcat 会乐意为每个请求开一个虚拟线程，
 * 上限变成 server.tomcat.max-connections（默认 8192）。看起来是"扛得更多了"，
 * 实际上只是把排队的位置往下游挪：真正的瓶颈变成 HikariCP 的连接数（默认 10）。
 * 于是失败模式从【干净地拒绝一部分请求】退化成【所有人一起卡在获取数据库连接、
 * 然后集体超时】—— 后者对下游更凶险，对用户体验也更差，而且排查时看到的是满屏
 * connection acquisition timeout，很难一眼看出根因是入口没有限流。
 * <p>
 * 所以这个类做的事，就是把原来"藏在线程池尺寸里的限流"变成一个【显式的、可调的、
 * 有名字的】东西。这也是把并发上限和"用多少线程"解耦：以前想调限流阈值只能去改线程池
 * 大小，两件事被迫绑在一起；现在各调各的。
 *
 * <h3>为什么是 tryAcquire 立即失败，而不是排队等待</h3>
 * 秒杀的特点是"绝大多数请求注定抢不到"。让抢不到的人排队等，只是把失败延后、
 * 同时把资源占住。立刻返回"系统繁忙"比让用户等 10 秒再失败更好，也让上游
 * （网关的令牌桶限流）能更快看到压力。
 *
 * <h3>和网关限流的关系：两层是互补的，不是重复</h3>
 * 网关那层（RequestRateLimiter）限的是【速率】（每秒多少个），防的是流量洪峰；
 * 这一层限的是【在途并发数】，防的是"请求都进来了但下游处理不过来、堆在这里"。
 * 一个慢下游会让速率限流失效（每秒放进来 50 个，但每个要 10 秒，在途就堆到 500），
 * 并发限流才管得住这种情况。
 */
@Component
public class SeckillBulkhead {

    private final Semaphore permits;
    private final int capacity;

    /** 被挡下的请求数。用 LongAdder 而不是 AtomicLong：高竞争下写入快得多，而读取精度要求不高。 */
    private final LongAdder rejected = new LongAdder();

    /**
     * @param capacity 允许同时在途的秒杀请求数。
     *                 <p>
     *                 <b>默认 32 是 2026-08-27 压测校准出来的</b>（此前是拍脑袋的 200）。
     *                 压测条件：mall-coupon 限 500m CPU / 768Mi，100 个会员 × 30 个活动
     *                 提供唯一「会员×活动」组合，k6 在集群内直压 /coupon/seckill/grab。
     *                 脚本见 mall-deploy/loadtest/seckill-grab.js。
     *
     *                 <h4>实测出来最重要的一件事：瓶颈不是速率，是冷启动</h4>
     *                 充分预热后 mall-coupon 在 500m CPU 下轻松吃下 70 rps
     *                 （2100/2100 全部成功，p95 82ms，闸门零拒绝）。
     *                 但<b>刚启动的同一个服务，50 rps 就会把自己搞死</b>：
     *                 p95 约 10 秒、大量 15 秒客户端超时、CPU 100%，
     *                 最后连存活探针都响应不过来，被 K8s SIGKILL。复现过两次。
     *                 <p>
     *                 原因是冷启动时几件事叠在一起：JVM 还在解释执行（JIT 没编译完）、
     *                 Hikari 连接池 / RabbitMQ 连接 / Redis 连接池 / Feign 的 Consul 与
     *                 负载均衡缓存<b>全都是第一个请求才惰性初始化</b>，而 500m CPU 上
     *                 JVM 只看到 1 核、GC 退化成 SerialGC。这些代价在空闲时无所谓，
     *                 一上量就追不上。
     *
     *                 <h4>为什么 200 是个放大器而不是保护</h4>
     *                 闸门的作用应该是「超过下游能力就立刻拒绝」。200 太大，实际效果是
     *                 把 200 个冷 JVM 根本喂不动的请求放进来一起排队，CPU 被彻底埋掉。
     *                 更糟的是<b>客户端超时并不会让服务端停止工作</b>：压测只跑 30 秒，
     *                 但积压让 pod 在两三分钟后才被探针杀掉 —— 一次 30 秒的流量尖峰
     *                 变成了几分钟的宕机。
     *                 <p>
     *                 32 是按 Little's law 反推的：热态 70 rps × 82ms ≈ 6 个在途请求，
     *                 留约 5 倍余量。两个方向都实测验证过：
     *                 <ul>
     *                   <li>冷态 50 rps：p95 从 9956ms 降到 3213ms，零客户端超时，
     *                       <b>pod 存活</b>（capacity=200 时同样负载下被 SIGKILL）；</li>
     *                   <li>热态 70 rps：<b>零拒绝</b>、p95 82ms —— 说明 32 不会误伤
     *                       系统本来能服务的请求。</li>
     *                 </ul>
     *
     *                 <h4>这个值的局限，改之前先读</h4>
     *                 冷态和热态的真实容量差了一个数量级，而这是个<b>静态</b>信号量，
     *                 所以 32 本质上是两者之间的折中。真正对症的做法是自适应并发限流
     *                 （按观测到的延迟动态调整许可数，如 Netflix concurrency-limits 的
     *                 AIMD/gradient 算法）。在上那个之前，更划算的是消掉冷启动惩罚本身：
     *                 启动时就把连接池、MQ、Redis 连接建好，而不是等第一个请求。
     *                 <p>
     *                 调大它不会让下游变快，只会让排队的位置从这里挪到连接池里。
     *                 观测指标：seckill_bulkhead_available_permits / _capacity / _rejected
     *                 （Micrometer 会剥掉 Gauge 的 _total 后缀）。判据是拒绝率和 p95
     *                 同时可接受：长期零拒绝说明闸门形同虚设，p95 到秒级说明开太大了。
    public SeckillBulkhead(@Value("${mall.seckill.bulkhead.capacity:32}") int capacity) {
        this.capacity = capacity;
        // 非公平信号量：公平模式要维护等待队列、吞吐明显更低，而这里本来就是
        // tryAcquire 立即返回、不存在"等待很久拿不到"的饥饿问题，公平性没有意义。
        this.permits = new Semaphore(capacity, false);
    }

    /**
     * 在闸门内执行。
     *
     * @param action   实际业务
     * @param rejected 没抢到通行证时的返回值（快速失败）
     */
    public <T> T call(Supplier<T> action, Supplier<T> rejected) {
        if (!permits.tryAcquire()) {
            this.rejected.increment();
            return rejected.get();
        }
        try {
            return action.get();
        } finally {
            // 必须在 finally 里释放。漏释放的后果是许可只减不增，闸门会越关越小，
            // 最终所有请求都被拒绝 —— 而且是不可逆的，只能重启 pod。
            permits.release();
        }
    }

    /** 当前可用许可数，供监控和排查用。 */
    public int availablePermits() {
        return permits.availablePermits();
    }

    public int capacity() {
        return capacity;
    }

    /** 累计被闸门挡下的请求数。持续增长说明容量或下游能力需要重新评估。 */
    public long rejectedCount() {
        return rejected.sum();
    }
}

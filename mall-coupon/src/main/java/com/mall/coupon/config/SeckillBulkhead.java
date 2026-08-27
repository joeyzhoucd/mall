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
     * @param capacity 允许同时在途的秒杀请求数。默认 200 是按这套本地集群的下游能力给的
     *                 起始值（Hikari 默认 10 个连接、Redis 单实例）。
     *                 <p>
     *                 <b>2026-08-27 压测进展</b>：这个值<b>仍未校准</b>，原因写在这里以免
     *                 下次又从头查一遍。已经量到的是框架层容量 —— 打一条下游不存在的
     *                 路径（只走到 DispatcherServlet 和拦截器链），mall-coupon 在 500m CPU
     *                 下 150 rps 时 p95 = 5ms、285 rps 时 p95 = 1033ms。
     *                 但那条路径<b>根本没经过这个闸门</b>，也没碰 Redis 和数据库，
     *                 所以它对 capacity 的取值毫无参考价值。
     *                 <p>
     *                 要校准必须压真实抢购路径，而那需要先灌秒杀数据
     *                 （sms_seckill_promotion / _session / _sku_relation 目前都是 0 行）
     *                 和测试会员。校准的判据是闸门的拒绝率和 p95 同时可接受：
     *                 拒绝率为 0 说明闸门形同虚设（瓶颈在别处、它没起作用），
     *                 p95 高到秒级说明放进来的并发已经超过下游能力、闸门开太大了。
     *                 观测指标已经埋好：seckill_bulkhead_available_permits /
     *                 _capacity / _rejected（注意 Micrometer 会剥掉 Gauge 的 _total 后缀）。
     *                 调大它不会让下游变快，只会让排队的位置从这里挪到连接池里。
     */
    public SeckillBulkhead(@Value("${mall.seckill.bulkhead.capacity:200}") int capacity) {
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

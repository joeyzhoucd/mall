package com.mall.coupon.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 领券路径的并发闸门。
 *
 * <h3>为什么需要它：2026-09-09 压测把它的必要性证明得很干净</h3>
 * 领券接口上线后第一次压测（200 rps × 20s，100 会员抢一张只发 10 张的券，
 * k6 在集群内直压 mall-coupon）：
 * <pre>
 *   hikaricp_connections_max            5
 *   hikaricp_connections_timeout_total  2064
 *   http_server_requests{status=500, exception=MyBatisSystemException}  2063
 * </pre>
 * 2064 次连接获取超时和 2063 个 500 <b>一比一</b> —— 每个 5xx 都是池耗尽。
 * 而 {@code connection-timeout} 已经是 3 秒（config-repo 里调过），
 * 所以 200 rps × 3s ≈ 600 个在途请求，正好是 k6 撞到的 VU 上限。
 * <p>
 * 也就是说：<b>限流不是"还没做"，而是入口处根本没有准入控制</b>，
 * 600 个请求全都挤到 5 个连接上，每个等 3 秒再一起失败。
 *
 * <h3>这件事 CouponWebConfig 的注释里早就预言过</h3>
 * 那里写着秒杀去掉有界线程池之后必须补显式限流，否则
 * "瓶颈会下移到数据库连接池，失败模式从『干净地拒绝』退化成
 * 『集体卡在获取连接然后一起超时』"。领券是新写的写路径，
 * 没有对应的守卫，于是一字不差地复现了同一个失败模式。
 *
 * <h3>为什么是【独立的】闸门，不和秒杀共用</h3>
 * <ul>
 *   <li><b>隔离</b>：bulkhead 的本义就是"一个舱进水不要沉了整条船"。
 *       共用一个闸门意味着领券洪峰会把秒杀的通行证也吃光。</li>
 *   <li><b>成本不同</b>：秒杀的热路径是 Redis + Lua，领券是一个数据库事务
 *       （COUNT + INSERT + 条件 UPDATE），两者的单请求占用时长差很多，
 *       合理容量自然不同。</li>
 *   <li><b>指标可分</b>：两条路径各自的 rejected 曲线才说明得了问题出在哪。</li>
 * </ul>
 *
 * <h3>【必须给所有注入点加 @Qualifier】否则整个服务起不来</h3>
 * {@link SeckillGrabController} 和 {@link SeckillMetrics} 原来是<b>按类型</b>
 * 注入 {@link SeckillBulkhead} 的。多了这第二个同类型 bean 之后，
 * 那些注入点会变成歧义（{@code NoUniqueBeanDefinitionException}）——
 * 编译完全通过，<b>启动直接崩</b>。所以那几处都补了显式限定符。
 * <p>
 * 刻意<b>不</b>用 {@code @Primary} 来"解决"这个问题：那样一来
 * "秒杀注入到哪个闸门"就变成一个隐式默认，读代码的人看不出来。
 *
 * <h3>关于复用 SeckillBulkhead 这个类型名</h3>
 * 这个接口除了名字之外是完全通用的（一个信号量 + 拒绝计数）。
 * 把它改名成 {@code Bulkhead} 要动 8 个文件，其中包括自适应实现和它的测试，
 * 而收益只是名字更准。这里选择复用类型、用 bean 名字区分，
 * 并把这个取舍写下来 —— 改名是一次干净的机械重构，随时可以做。
 */
@Configuration
public class CouponClaimBulkheadConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CouponClaimBulkheadConfiguration.class);

    /** 领券闸门的 bean 名。注入时用它做限定符。 */
    public static final String BEAN = "couponClaimBulkhead";

    /**
     * 固定容量的闸门。
     *
     * <h3>默认 16 是【起点】，不是校准结果</h3>
     * 秒杀那个 32 是压测校准出来的（见 {@link StaticSeckillBulkhead} 的类注释），
     * 这个 16 目前只是按 Hikari 池大小推的：池是 5，允许约 3 倍在途，
     * 让"正在用连接"和"刚拿到通行证还在排队"有一点重叠，又不至于堆出 3 秒的等待队列。
     * <p>
     * <b>必须用同样的方法校准一遍</b>：
     * <pre>
     *   ./reset-coupon.sh
     *   ./run.sh claim 200 20s SCRIPT=coupon-claim.js TARGET=http://mall-coupon:10000 K6_CPU=4
     * </pre>
     * 判据是两条一起看：
     * <ul>
     *   <li>{@code hikaricp_connections_timeout_total} 应当降到 <b>0</b>
     *       —— 还有超时说明闸门太松，没起到保护作用；</li>
     *   <li>成功数仍然是<b>恰好 10</b>，且 {@code coupon_claim_bulkhead_rejected}
     *       不应该在<b>低负载</b>下增长 —— 那说明闸门太紧、误伤了本来能服务的请求。</li>
     * </ul>
     * 秒杀那次就是靠"冷态不被 SIGKILL + 热态零拒绝"两个方向一起定下 32 的。
     *
     * <h3>只用静态实现，不上自适应</h3>
     * 自适应（{@link AdaptiveSeckillBulkhead}）按观测延迟动态调限额，
     * 形状上更对症，但引入一个会自己变的量。领券这条路目前连一个可信的基线都还没有，
     * 先用可预测的固定值把基线量出来；有了基线再谈要不要自适应。
     */
    @Bean(BEAN)
    public SeckillBulkhead couponClaimBulkhead(
            @Value("${mall.coupon.claim.bulkhead.capacity:16}") int capacity) {
        log.info("领券闸门: 静态模式，容量={}（默认 16 是按 Hikari 池 5 推的起点，需要压测校准）", capacity);
        return new StaticSeckillBulkhead(capacity);
    }

    // -----------------------------------------------------------------------
    // 指标。和秒杀那三个同构，但前缀不同，这样两条路径的曲线能分开看。
    // -----------------------------------------------------------------------

    /** 还能放进来多少个。长期贴近 0 说明闸门已成为瓶颈。 */
    @Bean
    public Gauge couponClaimBulkheadAvailablePermits(
            MeterRegistry registry, @Qualifier(BEAN) SeckillBulkhead bulkhead) {
        return Gauge.builder("coupon.claim.bulkhead.available.permits", bulkhead,
                        SeckillBulkhead::availablePermits)
                .description("领券闸门当前剩余通行证")
                .register(registry);
    }

    /** 当前并发上限。静态实现下是常量，画出来是为了和 available 对照看。 */
    @Bean
    public Gauge couponClaimBulkheadCapacity(
            MeterRegistry registry, @Qualifier(BEAN) SeckillBulkhead bulkhead) {
        return Gauge.builder("coupon.claim.bulkhead.capacity", bulkhead, SeckillBulkhead::capacity)
                .description("领券闸门并发上限")
                .register(registry);
    }

    /**
     * 累计被挡下的请求数。
     *
     * <p>注意 Micrometer 会剥掉 Gauge 名字上的 {@code _total} 后缀，
     * 所以 Prometheus 里查的是 {@code coupon_claim_bulkhead_rejected}。
     * 用 Gauge 而不是 Counter：计数本身由闸门内部的 LongAdder 维护，
     * 这里只是把它暴露出来；单调递增的 Gauge 一样可以用 rate()，
     * 只要记得 pod 重启后会归零。
     */
    @Bean
    public Gauge couponClaimBulkheadRejected(
            MeterRegistry registry, @Qualifier(BEAN) SeckillBulkhead bulkhead) {
        return Gauge.builder("coupon.claim.bulkhead.rejected.total", bulkhead,
                        SeckillBulkhead::rejectedCount)
                .description("领券闸门累计拒绝数")
                .register(registry);
    }
}

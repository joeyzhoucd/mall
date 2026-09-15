package com.mall.coupon.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 秒杀热路径的 JIT 预热：在 pod 被标记 Ready 之前，把抢购那条路径跑上几百遍。
 *
 * <h3>要解决的问题（有实测数字）</h3>
 * 2026-09-15 实测同一个 pod、同样 50 rps × 30s 打真实抢购路径：
 * <pre>
 *   刚就绪（全冷）  抢中 278   闸门拒绝 998(70.7%)  系统繁忙 136   p95 8375 ms
 *   跑热之后        抢中 1059  闸门拒绝 442(29.4%)  系统繁忙 0     p95 1928 ms
 * </pre>
 * <b>吞吐差 3.8 倍，p95 差 4.3 倍</b>，而且冷态会多出一百多次"系统繁忙"
 * （内部异常），热态是 0。
 * <p>
 * 更早的一轮（2026-08-27）里，冷启动下的 pod 甚至被存活探针 SIGKILL 过 ——
 * 请求积压把它自己拖死。当时靠把闸门容量从 200 收到 32 缓解了，
 * 但那只是"少放一些进来"，没有解决"进来的那些跑得慢"。
 *
 * <h3>为什么 pod 活了很久也可能是冷的</h3>
 * <b>JIT 的热度是按代码路径算的，不是按 pod 年龄算的。</b>
 * 实测：一个已经跑了 40 分钟的 pod，第一次打秒杀路径 p95 是 5798 ms，
 * 第二次就降到 1548 ms —— 它一直"活着"，但那条路径从没被执行过，
 * 所以仍然是解释执行的。
 * <p>
 * 这也是为什么现成的 {@code EagerConnectionWarmup} 不够：它只把连接池、
 * Redis、RabbitMQ 的<b>连接</b>建起来，一行业务代码都没跑过。
 *
 * <h3>怎么做到无副作用</h3>
 * 用一个<b>一定不存在的 relationId</b> 调 {@link SeckillGrabServiceImpl#grabInternal}。
 * 看 {@code seckill_grab.lua} 的返回值约定：{@code -2 = 活动不存在}。
 * 于是这条调用会完整走过
 * <pre>
 *   本地售罄缓存查询 → Redis Lua 脚本执行 → 返回码分支 → 构造结果对象
 * </pre>
 * 然后在拿到 -2 时<b>直接返回</b>，{@code doGrab()} 那一段（RabbitMQ 发布 +
 * 等 publisher confirm + 写本地消息表）根本不会执行。
 * <p>
 * 也就是说：<b>不扣任何库存、不发任何消息、不写任何数据、不碰任何真实活动</b>。
 * 代价是 MQ 和写库那半条路径预热不到 —— 那部分没有安全的空跑方式，
 * 硬要跑就会产生假订单。这是刻意的取舍，不是遗漏。
 *
 * <h3>为什么调 grabInternal 而不是 grab</h3>
 * {@code grab()} 外面包了一层 {@code businessMetrics.success/failure}。
 * 走它的话每次启动都会往业务指标里灌几百条"秒杀失败"，
 * 把 Grafana 的秒杀面板和告警规则搞脏 —— <b>预热不能污染观测数据</b>。
 * {@code grabInternal} 是包级可见的，这个类刻意放在同一个包里。
 *
 * <h3>为什么是 ApplicationRunner</h3>
 * 它在 {@code ApplicationReadyEvent} <b>之前</b>执行，而 Boot 的就绪状态
 * 是在那个事件之后才变成 ACCEPTING_TRAFFIC 的。也就是说预热期间
 * <b>k8s 还不会把流量路由进来</b> —— 这正是我们要的顺序：先热再接客。
 * 代价是启动时间变长，所以下面有时间预算兜底。
 */
@Component
@ConditionalOnProperty(name = "mall.warmup.seckill.enabled", matchIfMissing = true)
public class SeckillJitWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeckillJitWarmup.class);

    /**
     * 预热用的 relationId。<b>必须是一个不可能存在的值</b> —— Lua 脚本靠
     * "查不到库存 key" 返回 -2 来提前结束，一旦这个 id 撞上真实活动，
     * 预热就会真的去扣那个活动的库存。
     * <p>
     * 用 {@code Long.MAX_VALUE}：真实 relationId 来自数据库自增/雪花，
     * 不会取到这个值。
     */
    private static final long WARMUP_RELATION_ID = Long.MAX_VALUE;

    private final SeckillGrabServiceImpl grabService;

    /** 预热轮数。够让 C2 把热方法编译出来，又不至于让启动慢太多。 */
    @Value("${mall.warmup.seckill.iterations:400}")
    private int iterations;

    /**
     * 时间预算。Redis 慢或者不通的时候，预热不能把启动无限拖住 ——
     * pod 起不来比没预热严重得多。
     */
    @Value("${mall.warmup.seckill.budget-millis:8000}")
    private long budgetMillis;

    public SeckillJitWarmup(SeckillGrabServiceImpl grabService) {
        this.grabService = grabService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        long deadline = start + budgetMillis;
        int done = 0;
        try {
            for (int i = 0; i < iterations; i++) {
                if (System.currentTimeMillis() > deadline) {
                    break;
                }
                // memberId 也变着来：Lua 里有 SISMEMBER，固定值会让它一直走同一个分支。
                grabService.grabInternal(WARMUP_RELATION_ID, (long) -(i + 1), "warmup");
                done++;
            }
        } catch (Exception e) {
            // 【预热失败绝不能让 pod 起不来】没预热只是慢，起不来是彻底不可用。
            log.warn("秒杀热路径预热中断，跳过（已完成 {} 轮）", done, e);
            return;
        }
        long cost = System.currentTimeMillis() - start;
        if (done < iterations) {
            log.warn("秒杀热路径预热只跑了 {}/{} 轮就用光了 {} ms 预算 —— Redis 是不是慢？",
                    done, iterations, budgetMillis);
        } else {
            log.info("秒杀热路径预热完成 {} 轮，耗时 {} ms", done, cost);
        }
    }
}

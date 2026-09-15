package com.mall.coupon.service.impl;

import com.mall.coupon.vo.SeckillGrabResultVo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住秒杀 JIT 预热的三条不变量。
 *
 * <p>这个预热会在<b>每个 pod 每次启动时</b>跑几百遍抢购路径。
 * 它一旦有副作用，后果是"每次发版都莫名其妙少一批库存/多一批假订单"——
 * 而且因为发生在启动阶段、混在滚动更新里，极难联想到预热头上。
 * 所以这里把"无副作用"钉死。
 */
class SeckillJitWarmupTest {

    /** 记录每次调用的参数，用来断言"打的是哪个 relationId" */
    private record Call(Long relationId, Long memberId, String username) {
    }

    private SeckillGrabServiceImpl serviceRecording(List<Call> calls) {
        SeckillGrabServiceImpl service = mock(SeckillGrabServiceImpl.class);
        when(service.grabInternal(anyLong(), anyLong(), anyString()))
                .thenAnswer(inv -> {
                    calls.add(new Call(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
                    SeckillGrabResultVo vo = new SeckillGrabResultVo();
                    vo.setSuccess(false);
                    return vo;
                });
        return service;
    }

    private SeckillJitWarmup warmup(SeckillGrabServiceImpl service, int iterations, long budgetMillis) {
        SeckillJitWarmup w = new SeckillJitWarmup(service);
        ReflectionTestUtils.setField(w, "iterations", iterations);
        ReflectionTestUtils.setField(w, "budgetMillis", budgetMillis);
        ReflectionTestUtils.setField(w, "readOnlyIterations", iterations);
        return w;
    }

    /**
     * <b>本文件最重要的一条。</b>
     *
     * <p>预热必须用一个不可能存在的 relationId ——
     * {@code seckill_grab.lua} 靠"查不到库存 key"返回 -2 来提前结束，
     * 一旦这个 id 撞上真实活动，预热就会<b>真的扣掉那个活动的库存</b>，
     * 而且后面还会走进 doGrab()：发 MQ、写本地消息表、产生假订单。
     */
    @Test
    @DisplayName("预热只能打不可能存在的 relationId —— 撞上真实活动就会真扣库存")
    void onlyUsesAnImpossibleRelationId() {
        List<Call> calls = new ArrayList<>();
        warmup(serviceRecording(calls), 20, 5000).run(null);

        assertThat(calls).isNotEmpty();
        assertThat(calls).allSatisfy(c ->
                assertThat(c.relationId())
                        .as("预热用的 relationId 必须是 Long.MAX_VALUE；"
                                + "任何可能和真实活动撞上的值都会让预热真的扣库存、发 MQ")
                        .isEqualTo(Long.MAX_VALUE));
    }

    /**
     * 走 {@code grabInternal} 而不是 {@code grab}。
     *
     * <p>{@code grab()} 外面包着 businessMetrics 统计，走它的话每次启动都会往
     * 业务指标里灌几百条"秒杀失败"，Grafana 的秒杀面板和告警规则会被搞脏 ——
     * <b>预热不能污染观测数据</b>。
     */
    @Test
    @DisplayName("必须调 grabInternal，不能调 grab（否则污染业务指标）")
    void mustNotGoThroughTheMetricsWrapper() {
        SeckillGrabServiceImpl service = serviceRecording(new ArrayList<>());

        warmup(service, 5, 5000).run(null);

        verify(service, never()).grab(anyLong(), anyLong(), anyString());
    }

    /**
     * 时间预算必须真的生效。Redis 慢或不通时预热不能把启动无限拖住 ——
     * <b>pod 起不来比没预热严重得多</b>。
     */
    @Test
    @DisplayName("超出时间预算要停下，不能把启动无限拖住")
    void stopsWhenBudgetIsExhausted() {
        SeckillGrabServiceImpl slow = mock(SeckillGrabServiceImpl.class);
        when(slow.grabInternal(anyLong(), anyLong(), anyString()))
                .thenAnswer(inv -> {
                    Thread.sleep(20);       // 模拟很慢的 Redis
                    return new SeckillGrabResultVo();
                });

        long t0 = System.currentTimeMillis();
        // 10000 轮 × 20ms = 200 秒，但预算只有 300ms
        warmup(slow, 10000, 300).run(null);
        long cost = System.currentTimeMillis() - t0;

        assertThat(cost)
                .as("预算 300ms 却跑了 %d ms —— 预热没有被预算掐住，会拖垮启动", cost)
                .isLessThan(3000);
    }

    /**
     * 预热失败不能让 pod 起不来。没预热只是慢，起不来是彻底不可用。
     */
    @Test
    @DisplayName("预热抛异常时必须吞掉，不能让启动失败")
    void exceptionDuringWarmupDoesNotFailStartup() {
        SeckillGrabServiceImpl broken = mock(SeckillGrabServiceImpl.class);
        when(broken.grabInternal(anyLong(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("redis down"));

        // 不抛出来就算通过
        warmup(broken, 100, 5000).run(null);
    }

    /**
     * memberId 要变化。Lua 脚本里第一步是 {@code SISMEMBER userKey memberId}，
     * 固定 memberId 会让它每次都走同一个分支 —— 而 JIT 预热的价值
     * 恰恰在于把各个分支都跑到。
     */
    @Test
    @DisplayName("memberId 要变化，否则 Lua 里只有一个分支被跑到")
    void memberIdVaries() {
        List<Call> calls = new ArrayList<>();
        warmup(serviceRecording(calls), 30, 5000).run(null);

        long distinct = calls.stream().map(Call::memberId).distinct().count();
        assertThat(distinct)
                .as("%d 次调用只用了 %d 个不同的 memberId", calls.size(), distinct)
                .isGreaterThan(1);
    }

    /**
     * 只读热路径也必须被预热到，而且用的是合成 memberId。
     *
     * <p>第一版预热只跑 {@code grabInternal}，**实测毫无效果**：
     * 带预热的 pod 刚 Ready 时抢中 184、系统繁忙 277、p95 8923ms，
     * 同一个 pod 被真实流量跑过之后抢中 726、系统繁忙 66、p95 6276ms ——
     * 吞吐还是涨 3.9 倍，说明热错了地方。
     * <p>
     * {@code 系统繁忙} 是 {@code doGrab} 抛异常的计数，从 277 降到 66 指得很清楚：
     * 冷代价在 doGrab 那一段（尤其是那次跨服务 Feign 查地址），
     * 不在前面的 Redis Lua。所以补了 {@code warmupReadOnlyHotPath}。
     * <p>
     * 这条测试防的是它被误删或被跳过 —— 那样预热又会退回"热错地方"的状态，
     * 而且完全静默。
     */
    @Test
    @DisplayName("只读热路径（含跨服务查地址）必须也被预热到")
    void alsoWarmsTheReadOnlyHotPath() {
        List<Long> members = new ArrayList<>();
        SeckillGrabServiceImpl service = mock(SeckillGrabServiceImpl.class);
        when(service.grabInternal(anyLong(), anyLong(), anyString()))
                .thenReturn(new SeckillGrabResultVo());
        doAnswer(inv -> {
            members.add(inv.getArgument(0));
            return null;
        }).when(service).warmupReadOnlyHotPath(anyLong());

        warmup(service, 10, 5000).run(null);

        assertThat(members)
                .as("warmupReadOnlyHotPath 一次都没被调 —— 冷代价最大的那一段没被预热")
                .isNotEmpty();
        assertThat(members).allSatisfy(m ->
                assertThat(m)
                        .as("只读预热也必须用合成 memberId（负数），不能碰真实会员")
                        .isNegative());
    }

    /**
     * 负控：把轮数设成 0 时不该有任何调用。
     * 没有这一条的话，一个"忽略 iterations、固定跑 400 轮"的实现也能让上面几条通过。
     */
    @Test
    @DisplayName("负控：iterations=0 时一次都不调")
    void zeroIterationsDoesNothing() {
        AtomicInteger n = new AtomicInteger();
        SeckillGrabServiceImpl service = mock(SeckillGrabServiceImpl.class);
        when(service.grabInternal(anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    n.incrementAndGet();
                    return new SeckillGrabResultVo();
                });

        warmup(service, 0, 5000).run(null);

        assertThat(n.get()).isZero();
    }
}

package com.mall.coupon.service;

import com.mall.common.constant.ErrorCode;
import com.mall.common.metrics.BusinessMetrics;
import com.mall.coupon.dao.CouponDao;
import com.mall.coupon.dao.CouponHistoryDao;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.impl.CouponClaimServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 钉住领券重试循环的核心不变式：<b>绝不能用大于 per_limit 的 receive_seq 去插入</b>。
 *
 * <h3>为什么专门为这一条写测试</h3>
 * 2026-09-08 对集群真库做并发实验时，我先写的探针漏掉了
 * {@code already >= perLimit} 那道检查，结果 30 并发抢一张 {@code per_limit = 2}
 * 的券<b>发出了 3 张</b>（{@code receive_seq} = 1,2,3）。
 * <p>
 * 当时我在几处注释里写着"限领靠唯一索引，那个 COUNT 只是用来算 seq 的" ——
 * <b>这句话是错的</b>。唯一索引
 * {@code uk_member_coupon_seq (member_id, coupon_id, receive_seq)}
 * 只保证"一个 seq 槽位一个赢家"，它对"seq 该不该涨到 3"没有任何意见。
 * 真正给 seq 定上限的，就是这个类要保护的那道检查。
 * <p>
 * 这个不变式很容易在重构中被弄坏，而且<b>弄坏之后不会有任何报错</b>：
 * 券照发、接口照样返回成功，只有对账时才发现某些人多领了。
 * 所以它需要一个测试，而不是一句注释。
 *
 * <h3>为什么用假 DAO 而不是真数据库</h3>
 * 要测的是<b>循环的边界逻辑</b>，不是 SQL 的行为。用假 DAO 可以精确地
 * 制造"每次冲突后已领张数刚好加一"这种并发交错，而这在真库上是不确定的。
 * SQL 那一层（条件更新、唯一索引）由 {@code CouponClaimConcurrencyIT} 在
 * CI 里对真 MySQL 验证，两者互补。
 */
class CouponClaimSeqBoundTest {

    /** 记录每次 claim 被调用时传进来的 seq，测试结束后检查上界。 */
    private final List<Integer> attemptedSeqs = new ArrayList<>();

    private CouponEntity coupon(int perLimit, int publishCount) {
        CouponEntity c = new CouponEntity();
        c.setId(9001L);
        c.setPerLimit(perLimit);
        c.setPublishCount(publishCount);
        c.setReceiveCount(0);
        c.setPublish(1);
        c.setAmount(new BigDecimal("20.0000"));
        c.setMinPoint(new BigDecimal("199.0000"));
        c.setMemberLevel(0);
        c.setEndTime(new Date(System.currentTimeMillis() + 86_400_000L));
        return c;
    }

    /**
     * 装一个 service，其中：
     * <ul>
     *   <li>{@code countByMemberAndCoupon} 按 {@code counts} 依次返回 —— 模拟
     *       "每次重试时别的请求又占掉了一个槽位"</li>
     *   <li>{@code claim} 永远抛 {@link DuplicateKeyException} —— 模拟
     *       "每次都输掉这个 seq 的竞争"，把重试循环推到极限</li>
     * </ul>
     */
    private CouponClaimServiceImpl serviceThatAlwaysConflicts(CouponEntity coupon, int[] counts) {
        CouponDao couponDao = mock(CouponDao.class);
        CouponHistoryDao historyDao = mock(CouponHistoryDao.class);
        CouponClaimTxOps txOps = mock(CouponClaimTxOps.class);

        when(couponDao.selectById(anyLong())).thenReturn(coupon);

        // 依次返回 counts，用完之后一直返回最后一个值
        when(historyDao.countByMemberAndCoupon(anyLong(), anyLong())).thenAnswer(inv -> {
            int i = Math.min(attemptedSeqs.size(), counts.length - 1);
            return counts[i];
        });

        when(txOps.claim(any(), anyLong(), anyString(), anyInt())).thenAnswer(inv -> {
            attemptedSeqs.add(inv.getArgument(3));
            throw new DuplicateKeyException("seq 被并发占用");
        });

        CouponClaimServiceImpl service = new CouponClaimServiceImpl();
        ReflectionTestUtils.setField(service, "couponDao", couponDao);
        ReflectionTestUtils.setField(service, "couponHistoryDao", historyDao);
        ReflectionTestUtils.setField(service, "txOps", txOps);
        ReflectionTestUtils.setField(service, "businessMetrics",
                new BusinessMetrics(new SimpleMeterRegistry()));
        return service;
    }

    @Test
    @DisplayName("per_limit=2：即使每次都撞唯一索引，也绝不会尝试 seq=3")
    void neverAttemptsSeqBeyondPerLimit() {
        CouponEntity coupon = coupon(2, 20000);
        // 模拟最恶劣的交错：第一次看到 0 张，重试时看到 1 张，再重试看到 2 张。
        // 如果循环里少了 already >= perLimit 检查，第三轮就会拿 seq=3 去插。
        CouponClaimServiceImpl service = serviceThatAlwaysConflicts(coupon, new int[]{0, 1, 2, 2, 2});

        CouponClaimService.ClaimResult result = service.receive(9001L, 8000001L, "lt0001");

        assertThat(result.ok()).isFalse();
        assertThat(result.code()).isEqualTo(ErrorCode.COUPON_PER_LIMIT_EXCEEDED);
        assertThat(attemptedSeqs)
                .as("尝试过的 receive_seq 必须全部 <= per_limit(2)；出现 3 就是超领"
                        + "（实测过：真库上这会实际发出第 3 张券）")
                .allSatisfy(seq -> assertThat(seq).isLessThanOrEqualTo(2));
    }

    @Test
    @DisplayName("per_limit=1：已经领过一张时，一次都不该尝试插入")
    void doesNotAttemptWhenAlreadyAtLimit() {
        CouponEntity coupon = coupon(1, 5000);
        CouponClaimServiceImpl service = serviceThatAlwaysConflicts(coupon, new int[]{1});

        CouponClaimService.ClaimResult result = service.receive(9001L, 8000001L, "lt0001");

        assertThat(result.code()).isEqualTo(ErrorCode.COUPON_PER_LIMIT_EXCEEDED);
        assertThat(attemptedSeqs)
                .as("已达上限时还去 INSERT，等于把限领判断交给唯一索引 —— "
                        + "而索引只挡重复 seq，不挡 seq 变大")
                .isEmpty();
    }

    /**
     * 重试次数必须有界。
     *
     * <p>无界重试在热点券上会变成活锁：每个请求都在"数一次、撞一次"之间打转，
     * 把 Tomcat/虚拟线程和数据库连接全耗在这里，而调用方看到的只是响应变慢。
     * 这个测试确认循环会自己退出。
     */
    @Test
    @DisplayName("重试有界：一直冲突时循环会退出而不是打转")
    void retryIsBounded() {
        CouponEntity coupon = coupon(3, 20000);
        // 已领张数永远是 0 —— 也就是永远通过 perLimit 检查、永远撞索引。
        // 没有上界的话这里会死循环。
        CouponClaimServiceImpl service = serviceThatAlwaysConflicts(coupon, new int[]{0});

        CouponClaimService.ClaimResult result = service.receive(9001L, 8000001L, "lt0001");

        assertThat(result.ok()).isFalse();
        assertThat(attemptedSeqs)
                .as("重试次数必须有界（当前实现是 perLimit + 2）")
                .hasSizeLessThanOrEqualTo(coupon.getPerLimit() + 2);
    }
}

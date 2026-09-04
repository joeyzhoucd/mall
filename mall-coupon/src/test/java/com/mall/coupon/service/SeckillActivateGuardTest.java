package com.mall.coupon.service;

import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.impl.SeckillSchedulerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住后台激活入口的那条防超卖规则。
 *
 * <h3>为什么这条规则值得单独一个测试类</h3>
 * {@code SeckillGrabService.activate()} 做的是<b>重置</b>：
 * 库存计数写回 {@code seckillCount}，并且<b>删掉</b> {@code seckill:user:{id}}
 * 那个每人限购的记录键。
 *
 * 所以对一场<b>正在进行</b>的秒杀再点一次「激活」：
 * <ol>
 *   <li>库存回满 —— 已经卖出去的不算了</li>
 *   <li>限购记录清空 —— <b>所有已经抢中的人都能再抢一次</b></li>
 * </ol>
 * 超卖立刻发生，而界面上只是「点了一下按钮」。
 *
 * 后台的激活按钮是个随手可点的东西，所以这条拒绝必须有测试兜着 ——
 * 把它去掉不会有任何编译错误，也不会在正常路径上表现出任何异常，
 * 只有在「有人对进行中的活动点了第二次」时才会炸，而那时候已经超卖了。
 */
class SeckillActivateGuardTest {

    private SeckillSchedulerServiceImpl service;
    private SeckillSkuRelationService relationService;
    private SeckillGrabService grabService;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        relationService = mock(SeckillSkuRelationService.class);
        grabService = mock(SeckillGrabService.class);
        redis = mock(StringRedisTemplate.class);
        service = new SeckillSchedulerServiceImpl();
        ReflectionTestUtils.setField(service, "seckillSkuRelationService", relationService);
        ReflectionTestUtils.setField(service, "seckillGrabService", grabService);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
    }

    private SeckillSkuRelationEntity relation(long id, String count) {
        SeckillSkuRelationEntity r = new SeckillSkuRelationEntity();
        r.setId(id);
        r.setSkuId(1001L);
        r.setSeckillCount(count == null ? null : new BigDecimal(count));
        return r;
    }

    /** 模拟「Redis 里有没有库存键」，也就是活动有没有上线。 */
    private void stubActivated(long relationId, boolean activated) {
        when(redis.hasKey("seckill:stock:" + relationId)).thenReturn(activated);
    }

    @Test
    @DisplayName("没上线的活动可以激活，并且真的调到了 GrabService")
    void activatesWhenNotYetLive() {
        when(relationService.getById(9001L)).thenReturn(relation(9001L, "500"));
        stubActivated(9001L, false);
        when(grabService.activate(9001L)).thenReturn(true);

        service.activate(9001L);

        // 必须确认真的委托下去了。自己在 Scheduler 里重写一遍激活逻辑
        // 一定会漏掉其中一步（库存键、限购键、商品信息缓存、跨 pod 广播），
        // 而漏掉广播的表现是「别的 pod 上抢不到」，极难查。
        verify(grabService).activate(9001L);
    }

    @Test
    @DisplayName("已经上线的活动必须拒绝 —— 激活会重置库存并清空限购，直接超卖")
    void refusesWhenAlreadyLive() {
        when(relationService.getById(9001L)).thenReturn(relation(9001L, "500"));
        stubActivated(9001L, true);

        assertThatThrownBy(() -> service.activate(9001L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已经上线")
                .hasMessageContaining("超卖");

        // 关键：一次都不能调下去。只报错但仍然执行了，等于没有这条守卫。
        verify(grabService, never()).activate(anyLong());
    }

    @Test
    @DisplayName("配置不存在时拒绝，且不去问 Redis、也不激活")
    void refusesWhenRelationMissing() {
        when(relationService.getById(404L)).thenReturn(null);

        assertThatThrownBy(() -> service.activate(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不存在");

        verify(redis, never()).hasKey(anyString());
        verify(grabService, never()).activate(anyLong());
    }

    @Test
    @DisplayName("relationId 为空时拒绝，不要变成一次 getById(null)")
    void refusesNullId() {
        assertThatThrownBy(() -> service.activate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");

        verify(relationService, never()).getById(anyLong());
    }

    /**
     * GrabService.activate 返回 false 时要抛出可读的错误，而不是静默成功。
     *
     * 它只在「关系不存在或 seckillCount 为空」时返回 false。关系存在已经查过了，
     * 所以走到这里就是没设置秒杀总量 —— 那种情况下库存键会被写成 "null"，
     * 前台抢购会拿到一个无意义的数字。
     */
    @Test
    @DisplayName("GrabService 返回 false 时要报错，不能当成激活成功")
    void surfacesActivationFailure() {
        when(relationService.getById(9002L)).thenReturn(relation(9002L, null));
        stubActivated(9002L, false);
        when(grabService.activate(9002L)).thenReturn(false);

        assertThatThrownBy(() -> service.activate(9002L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("秒杀总量");
    }

    /**
     * 反向对照：确认 stubActivated 这个桩真的在起作用。
     *
     * 如果 hasKey 的桩没配对（比如键名拼错），mock 会返回 null，
     * {@code Boolean.TRUE.equals(null)} 是 false —— 于是「已上线」那条测试
     * 会因为「看起来没上线」而<b>意外通过</b>，给出虚假的安全感。
     * 这里锁住键名的确切形态。
     */
    @Test
    @DisplayName("反向对照：库存键的名字必须是 seckill:stock:{id}")
    void stockKeyNameIsExact() {
        when(relationService.getById(7L)).thenReturn(relation(7L, "10"));
        when(redis.hasKey("seckill:stock:7")).thenReturn(true);
        // 【必须把 grabService 打桩成成功】否则它默认返回 false，
        // 代码会抛「没设置秒杀总量」那个 IllegalStateException ——
        // 于是这条测试在守卫被删掉之后【仍然通过】，只是通过的理由不对。
        // 第一版就是这样，靠正向对照（把守卫改成 if(false)）才发现：
        // 那次只有 refusesWhenAlreadyLive 失败，这条却安然通过。
        when(grabService.activate(7L)).thenReturn(true);

        assertThatThrownBy(() -> service.activate(7L))
                .isInstanceOf(IllegalStateException.class)
                // 断言到具体措辞，把「被守卫拦住」和「激活本身失败」区分开。
                .hasMessageContaining("已经上线");

        // 换一个键名就不该被拦住 —— 证明上面拦住它靠的确实是这个键，
        // 而不是别的什么原因。
        SeckillSchedulerServiceImpl other = new SeckillSchedulerServiceImpl();
        StringRedisTemplate otherRedis = mock(StringRedisTemplate.class);
        SeckillGrabService otherGrab = mock(SeckillGrabService.class);
        ReflectionTestUtils.setField(other, "seckillSkuRelationService", relationService);
        ReflectionTestUtils.setField(other, "seckillGrabService", otherGrab);
        ReflectionTestUtils.setField(other, "redisTemplate", otherRedis);
        when(otherRedis.hasKey("seckill:stock:wrong")).thenReturn(true);
        when(otherGrab.activate(7L)).thenReturn(true);

        other.activate(7L);
        assertThat(true).isTrue(); // 没抛异常就说明拦截确实依赖那个精确键名
        verify(otherGrab).activate(7L);
    }
}

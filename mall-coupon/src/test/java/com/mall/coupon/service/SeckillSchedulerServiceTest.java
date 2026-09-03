package com.mall.coupon.service;

import com.mall.coupon.entity.SeckillSessionEntity;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.impl.SeckillSchedulerServiceImpl;
import com.mall.coupon.vo.SeckillSchedulerVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /coupon/seckill/scheduler/save} 的行为约束。
 *
 * <h3>这个测试真正要守的东西</h3>
 * 「保存一个秒杀配置」看起来是普通的 CRUD，实际上踩在三条不能破的线上：
 * <ul>
 *   <li><b>不能对已激活的活动改库存</b> —— 改了要 activate 才生效，
 *       而 activate 会清空「谁已抢过」，进行中执行等于直接超卖。
 *       这条规则在 {@code SeckillSkuRelationController#update} 里已经有了，
 *       新接口必须<b>同样</b>守住，否则等于开了个后门绕过它。</li>
 *   <li><b>同一场次同一 SKU 只能有一条关系</b> —— 每条关系在 Redis 里有独立的
 *       {@code seckill:stock:{id}}，重复插入就是同一个商品有两份库存、
 *       前台出现两个一样的条目各自能抢。</li>
 *   <li><b>时间必须是墙上时间原样落库</b> —— 库里是不带时区的 datetime，
 *       容器时区是 UTC 而业务时区是 Asia/Shanghai，任何一处折算都会差 8 小时且不报错。</li>
 * </ul>
 */
class SeckillSchedulerServiceTest {

    private SeckillSchedulerServiceImpl service;
    private SeckillSessionService sessionService;
    private SeckillSkuRelationService relationService;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        sessionService = mock(SeckillSessionService.class);
        relationService = mock(SeckillSkuRelationService.class);
        redis = mock(StringRedisTemplate.class);
        service = new SeckillSchedulerServiceImpl();
        ReflectionTestUtils.setField(service, "seckillSessionService", sessionService);
        ReflectionTestUtils.setField(service, "seckillSkuRelationService", relationService);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
    }

    private SeckillSchedulerVo validVo() {
        SeckillSchedulerVo vo = new SeckillSchedulerVo();
        vo.setSkuId(1001L);
        vo.setStartTime("2026-09-10 10:00:00");
        vo.setEndTime("2026-09-10 12:00:00");
        vo.setSeckillPrice(new BigDecimal("9.90"));
        vo.setSeckillCount(new BigDecimal("100"));
        vo.setSeckillLimit(new BigDecimal("2"));
        return vo;
    }

    /** 让 save(session) 像真的 insert 一样回填自增 id。 */
    private void stubSessionInsert(long id) {
        doAnswer(inv -> {
            ((SeckillSessionEntity) inv.getArgument(0)).setId(id);
            return true;
        }).when(sessionService).save(any(SeckillSessionEntity.class));
    }

    private void stubRelationInsert(long id) {
        doAnswer(inv -> {
            ((SeckillSkuRelationEntity) inv.getArgument(0)).setId(id);
            return true;
        }).when(relationService).save(any(SeckillSkuRelationEntity.class));
    }

    // ------------------------------------------------------------------ 正常路径

    @Test
    @DisplayName("全新配置：建场次 + 建关系，返回关系 id，且 soldCount 从 0 开始")
    void createsSessionAndRelation() {
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(null);
        when(relationService.getOne(any(), anyBoolean())).thenReturn(null);
        stubSessionInsert(7L);
        stubRelationInsert(77L);

        Long id = service.save(validVo());

        assertThat(id).isEqualTo(77L);
        ArgumentCaptor<SeckillSkuRelationEntity> cap = ArgumentCaptor.forClass(SeckillSkuRelationEntity.class);
        verify(relationService).save(cap.capture());
        SeckillSkuRelationEntity saved = cap.getValue();
        assertThat(saved.getPromotionSessionId()).isEqualTo(7L);
        assertThat(saved.getSkuId()).isEqualTo(1001L);
        assertThat(saved.getSeckillPrice()).isEqualByComparingTo("9.90");
        assertThat(saved.getSeckillCount()).isEqualByComparingTo("100");
        assertThat(saved.getSoldCount()).isZero();
    }

    @Test
    @DisplayName("时间必须原样落库：填 10:00 就是 10:00，不能被时区折算掉")
    void storesWallClockTime() {
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(null);
        when(relationService.getOne(any(), anyBoolean())).thenReturn(null);
        stubSessionInsert(7L);
        stubRelationInsert(77L);

        service.save(validVo());

        ArgumentCaptor<SeckillSessionEntity> cap = ArgumentCaptor.forClass(SeckillSessionEntity.class);
        verify(sessionService).save(cap.capture());
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        // 用默认时区格式化 —— 和写入时用的是同一个时区，一进一出必须抵消。
        assertThat(fmt.format(cap.getValue().getStartTime())).isEqualTo("2026-09-10 10:00:00");
        assertThat(fmt.format(cap.getValue().getEndTime())).isEqualTo("2026-09-10 12:00:00");
    }

    @Test
    @DisplayName("同样的时间段复用已有场次，不重复建")
    void reusesExistingSession() {
        SeckillSessionEntity existing = new SeckillSessionEntity();
        existing.setId(42L);
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(existing);
        when(relationService.getOne(any(), anyBoolean())).thenReturn(null);
        stubRelationInsert(88L);

        service.save(validVo());

        verify(sessionService, never()).save(any(SeckillSessionEntity.class));
        ArgumentCaptor<SeckillSkuRelationEntity> cap = ArgumentCaptor.forClass(SeckillSkuRelationEntity.class);
        verify(relationService).save(cap.capture());
        assertThat(cap.getValue().getPromotionSessionId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("同场次同 SKU 重复保存走更新，不再插一条")
    void updatesInsteadOfDuplicating() {
        SeckillSessionEntity session = new SeckillSessionEntity();
        session.setId(42L);
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(session);

        SeckillSkuRelationEntity existing = new SeckillSkuRelationEntity();
        existing.setId(99L);
        existing.setSeckillCount(new BigDecimal("100"));
        when(relationService.getOne(any(), anyBoolean())).thenReturn(existing);
        when(redis.hasKey(anyString())).thenReturn(false);

        Long id = service.save(validVo());

        assertThat(id).isEqualTo(99L);
        verify(relationService, never()).save(any(SeckillSkuRelationEntity.class));
        verify(relationService).updateById(any(SeckillSkuRelationEntity.class));
    }

    // -------------------------------------------------- 核心：不能给已激活的活动改库存

    @Test
    @DisplayName("已激活时改库存必须拒绝，且一行都不能写")
    void refusesStockChangeWhenActivated() {
        SeckillSessionEntity session = new SeckillSessionEntity();
        session.setId(42L);
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(session);

        SeckillSkuRelationEntity existing = new SeckillSkuRelationEntity();
        existing.setId(99L);
        existing.setSeckillCount(new BigDecimal("100"));
        when(relationService.getOne(any(), anyBoolean())).thenReturn(existing);
        // seckill:stock:99 存在 = 已经上线
        when(redis.hasKey("seckill:stock:99")).thenReturn(true);

        SeckillSchedulerVo vo = validVo();
        vo.setSeckillCount(new BigDecimal("500"));   // 想把库存从 100 改成 500

        assertThatThrownBy(() -> service.save(vo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已经激活")
                .hasMessageContaining("超卖");
        verify(relationService, never()).updateById(any(SeckillSkuRelationEntity.class));
        verify(relationService, never()).save(any(SeckillSkuRelationEntity.class));
    }

    @Test
    @DisplayName("负控制：已激活但【不改】库存时应当放行，只改价格和限购")
    void allowsPriceChangeWhenActivated() {
        // 没有这条对照，上一条测试用一个「已激活就全拒绝」的实现也能通过 ——
        // 那样管理员连改个价格都做不到，规则就过严了。
        SeckillSessionEntity session = new SeckillSessionEntity();
        session.setId(42L);
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(session);

        SeckillSkuRelationEntity existing = new SeckillSkuRelationEntity();
        existing.setId(99L);
        existing.setSeckillCount(new BigDecimal("100"));
        when(relationService.getOne(any(), anyBoolean())).thenReturn(existing);
        when(redis.hasKey("seckill:stock:99")).thenReturn(true);

        SeckillSchedulerVo vo = validVo();
        vo.setSeckillCount(new BigDecimal("100"));   // 和现状一致 = 没改
        vo.setSeckillPrice(new BigDecimal("7.70"));

        Long id = service.save(vo);

        assertThat(id).isEqualTo(99L);
        ArgumentCaptor<SeckillSkuRelationEntity> cap = ArgumentCaptor.forClass(SeckillSkuRelationEntity.class);
        verify(relationService).updateById(cap.capture());
        assertThat(cap.getValue().getSeckillPrice()).isEqualByComparingTo("7.70");
        // 关键：放行的那次更新里【不能】带 seckillCount，否则等于绕过了上面那条规则
        assertThat(cap.getValue().getSeckillCount()).isNull();
    }

    // ------------------------------------------------------------------ 参数校验

    @Test
    @DisplayName("参数不合法一律拒绝，且不写库")
    void rejectsInvalidInput() {
        record Case(String name, Consumer<SeckillSchedulerVo> mutate, String expect) { }
        Case[] cases = {
                new Case("skuId 为空", v -> v.setSkuId(null), "skuId"),
                new Case("开始时间为空", v -> v.setStartTime(null), "开始时间"),
                new Case("时间格式是 ISO 串（前端漏了 value-format）",
                        v -> v.setStartTime("2026-09-10T02:00:00.000Z"), "yyyy-MM-dd HH:mm:ss"),
                new Case("结束早于开始", v -> v.setEndTime("2026-09-10 09:00:00"), "晚于"),
                new Case("结束等于开始", v -> v.setEndTime("2026-09-10 10:00:00"), "晚于"),
                new Case("价格为 0", v -> v.setSeckillPrice(BigDecimal.ZERO), "秒杀价"),
                new Case("价格为负", v -> v.setSeckillPrice(new BigDecimal("-1")), "秒杀价"),
                new Case("总量为 0", v -> v.setSeckillCount(BigDecimal.ZERO), "秒杀总量"),
                new Case("限购为 0", v -> v.setSeckillLimit(BigDecimal.ZERO), "每人限购"),
                new Case("限购大于总量",
                        v -> v.setSeckillLimit(new BigDecimal("101")), "不能大于"),
        };

        for (Case c : cases) {
            setUp();
            SeckillSchedulerVo vo = validVo();
            c.mutate().accept(vo);
            assertThatThrownBy(() -> service.save(vo))
                    .as("用例：%s", c.name())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(c.expect());
            verify(sessionService, never()).save(any(SeckillSessionEntity.class));
            verify(relationService, never()).save(any(SeckillSkuRelationEntity.class));
        }
        assertThat(cases.length).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("负控制：合法输入不能被上面那批校验误伤")
    void acceptsValidInput() {
        // 没有这条，一个「无脑抛异常」的实现能让上一条测试全绿。
        when(sessionService.getOne(any(), anyBoolean())).thenReturn(null);
        when(relationService.getOne(any(), anyBoolean())).thenReturn(null);
        stubSessionInsert(7L);
        stubRelationInsert(77L);

        assertThat(service.save(validVo())).isEqualTo(77L);
    }
}

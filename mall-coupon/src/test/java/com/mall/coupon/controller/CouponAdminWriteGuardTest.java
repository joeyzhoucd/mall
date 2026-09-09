package com.mall.coupon.controller;

import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponEntity;
import com.mall.coupon.service.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台券写接口的守护测试。
 *
 * <h3>为什么这几条必须有测试而不是注释</h3>
 * 它们保护的都是<b>编译通过、界面显示成功、只有对账时才发现</b>的那类错误：
 * <ul>
 *   <li>编辑券把 {@code receive_count} 整行写回 -> 上限凭空变大 -> 超发</li>
 *   <li>删掉已被领取的券 -> 用户手里的券从"我的优惠券"里凭空消失</li>
 *   <li>发行量改到小于已领量 -> 券立刻变成"已领完"，而已发出的收不回来</li>
 * </ul>
 * 三者都不会抛异常、不会有日志报错。
 */
class CouponAdminWriteGuardTest {

    private CouponEntity validDraft() {
        CouponEntity c = new CouponEntity();
        c.setCouponName("满 199 减 20");
        c.setAmount(new BigDecimal("20.0000"));
        c.setMinPoint(new BigDecimal("199.0000"));
        c.setPublishCount(5000);
        c.setPerLimit(1);
        c.setUseType(0);
        c.setMemberLevel(0);
        c.setPublish(1);
        return c;
    }

    private CouponController controller(CouponService service) {
        CouponController controller = new CouponController();
        ReflectionTestUtils.setField(controller, "couponService", service);
        return controller;
    }

    /**
     * 这是这个文件里最重要的一条。
     *
     * <p>MyBatis-Plus 的 {@code updateById} 把实体里所有非空字段整行写回。
     * 后台编辑是"GET 详情 -> 改几个字段 -> POST 回来"，请求体里的
     * {@code receiveCount} 是<b>打开表单那一刻</b>的旧值。原样写回等于：
     * <pre>
     *   10:00 打开表单          receive_count = 40
     *   10:00-10:05 用户领 60 张  receive_count = 100（到达上限）
     *   10:05 点保存            receive_count 被写回 40 -> 上限多出 60 张
     * </pre>
     * 这就是超发。mall-ware 的 {@code WareOrderTaskDetailDao} 注释里
     * 记录过同一个机制的另一次事故（库存被释放两次）。
     */
    @Test
    @DisplayName("修改券时绝不能把 receive_count / use_count 写回 —— 那会直接造成超发")
    void updateMustNotWriteBackCounters() {
        CouponService service = mock(CouponService.class);
        CouponEntity existing = validDraft();
        existing.setId(9001L);
        existing.setReceiveCount(100);
        existing.setUseCount(7);
        when(service.getById(anyLong())).thenReturn(existing);
        when(service.updateById(any())).thenReturn(true);

        CouponEntity submitted = validDraft();
        submitted.setId(9001L);
        // 表单带回来的是打开页面那一刻的旧值
        submitted.setReceiveCount(40);
        submitted.setUseCount(3);

        R result = controller(service).update(submitted);

        assertThat(result.getCode()).isZero();
        ArgumentCaptor<CouponEntity> captor = ArgumentCaptor.forClass(CouponEntity.class);
        verify(service).updateById(captor.capture());
        assertThat(captor.getValue().getReceiveCount())
                .as("receiveCount 必须是 null，MyBatis-Plus 才会跳过这一列；"
                        + "写回 40 会把上限凭空撑开 60 张")
                .isNull();
        assertThat(captor.getValue().getUseCount())
                .as("useCount 同理，它也只应由用券流程推进")
                .isNull();
    }

    @Test
    @DisplayName("新建券时计数器强制归零，不采信请求体")
    void saveForcesCountersToZero() {
        CouponService service = mock(CouponService.class);
        when(service.save(any())).thenReturn(true);

        CouponEntity submitted = validDraft();
        // 恶意/错误的请求体：把已领量写成上限，券一建出来就是废的
        submitted.setReceiveCount(9999);
        submitted.setUseCount(9999);
        submitted.setId(123L);

        controller(service).save(submitted);

        ArgumentCaptor<CouponEntity> captor = ArgumentCaptor.forClass(CouponEntity.class);
        verify(service).save(captor.capture());
        assertThat(captor.getValue().getReceiveCount()).isZero();
        assertThat(captor.getValue().getUseCount()).isZero();
        assertThat(captor.getValue().getId())
                .as("id 必须由数据库生成，不能让请求体指定 —— 否则能覆盖任意一张券")
                .isNull();
    }

    @Test
    @DisplayName("已经有人领过的券不能删除 —— 否则用户手里的券会凭空消失")
    void deleteRefusesClaimedCoupon() {
        CouponService service = mock(CouponService.class);
        CouponEntity claimed = validDraft();
        claimed.setId(9001L);
        claimed.setReceiveCount(3);
        when(service.listByIds(any())).thenReturn(List.of(claimed));

        R result = controller(service).delete(new Long[]{9001L});

        assertThat(result.getCode()).isNotZero();
        assertThat(String.valueOf(result.get("msg"))).contains("已被领取");
        verify(service, never()).removeByIds(any());
    }

    @Test
    @DisplayName("没人领过的券可以删")
    void deleteAllowsUnclaimedCoupon() {
        CouponService service = mock(CouponService.class);
        CouponEntity fresh = validDraft();
        fresh.setId(9001L);
        fresh.setReceiveCount(0);
        when(service.listByIds(any())).thenReturn(List.of(fresh));
        when(service.removeByIds(any())).thenReturn(true);

        R result = controller(service).delete(new Long[]{9001L});

        assertThat(result.getCode()).isZero();
        verify(service).removeByIds(any());
    }

    @Test
    @DisplayName("发行量不能改到小于已领取量")
    void updateRefusesShrinkingBelowClaimed() {
        CouponService service = mock(CouponService.class);
        CouponEntity existing = validDraft();
        existing.setId(9001L);
        existing.setReceiveCount(100);
        when(service.getById(anyLong())).thenReturn(existing);

        CouponEntity submitted = validDraft();
        submitted.setId(9001L);
        submitted.setPublishCount(50);

        R result = controller(service).update(submitted);

        assertThat(result.getCode()).isNotZero();
        verify(service, never()).updateById(any());
    }

    @Test
    @DisplayName("暂未实现的限制要明确拒绝，不能静默接受一个没人能领的券")
    void rejectsUnimplementedRestrictions() {
        CouponService service = mock(CouponService.class);

        CouponEntity levelGated = validDraft();
        levelGated.setMemberLevel(2);
        assertThat(controller(service).save(levelGated).getCode())
                .as("等级限制无法校验（ums_member_level 是空表），领取接口会一律拒绝。"
                        + "静默接受等于让运营建一个没人能领的活动")
                .isNotZero();

        CouponEntity scoped = validDraft();
        scoped.setUseType(2);
        assertThat(controller(service).save(scoped).getCode())
                .as("指定商品券需要 oms_order_item.spu_id，而那一列从来不写")
                .isNotZero();

        verify(service, never()).save(any());
    }

    @Test
    @DisplayName("基础校验：券名、面额、发行量、限领、时间先后")
    void validatesBasicFields() {
        CouponService service = mock(CouponService.class);
        CouponController controller = controller(service);

        CouponEntity noName = validDraft();
        noName.setCouponName("  ");
        assertThat(controller.save(noName).getCode()).isNotZero();

        CouponEntity zeroAmount = validDraft();
        zeroAmount.setAmount(BigDecimal.ZERO);
        assertThat(controller.save(zeroAmount).getCode()).isNotZero();

        CouponEntity zeroPublish = validDraft();
        zeroPublish.setPublishCount(0);
        assertThat(controller.save(zeroPublish).getCode()).isNotZero();

        CouponEntity zeroPerLimit = validDraft();
        zeroPerLimit.setPerLimit(0);
        assertThat(controller.save(zeroPerLimit).getCode()).isNotZero();

        // 领到手即过期：end_time 早于 enable_start_time
        CouponEntity expiredOnArrival = validDraft();
        long now = System.currentTimeMillis();
        expiredOnArrival.setEnableStartTime(new Date(now + 86_400_000L));
        expiredOnArrival.setEndTime(new Date(now + 3_600_000L));
        assertThat(controller.save(expiredOnArrival).getCode()).isNotZero();

        verify(service, never()).save(any());
    }
}

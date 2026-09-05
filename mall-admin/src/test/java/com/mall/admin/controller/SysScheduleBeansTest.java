package com.mall.admin.controller;

import com.mall.admin.schedule.ScheduledTask;
import com.mall.admin.service.ScheduleJobService;
import com.mall.common.utils.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住「可用任务 bean」这个接口的两条性质。
 *
 * <h3>1. 它只能列出带 @ScheduledTask 的 bean</h3>
 * 这个接口存在的唯一理由是让前端把 beanName 做成下拉，
 * 免得管理员填错了要等任务半夜真的跑起来才发现。
 *
 * 但它<b>绝不能</b>变成「列出容器里所有 bean」——那等于把
 * 「有哪些东西可以被调用」这件事的答案直接摆出来，
 * 而白名单的意义正是「只有极少数几个能被调用」。
 * 所以这里断言它查询的是<b>带注解的那一组</b>，而不是全部。
 *
 * <h3>2. 它不改变执行期的校验</h3>
 * 真正拦住「调用任意 bean」的是 ScheduleJobExecutor 里那道
 * {@code targetClass.isAnnotationPresent(ScheduledTask.class)}。
 * 这个接口只是把同一份名单读出来给人看，
 * 没有、也不该有任何「因为在列表里所以放行」的逻辑。
 */
class SysScheduleBeansTest {

    private SysScheduleController controller(ApplicationContext ctx) {
        SysScheduleController c = new SysScheduleController(mock(ScheduleJobService.class));
        ReflectionTestUtils.setField(c, "applicationContext", ctx);
        return c;
    }

    @Test
    @DisplayName("查的是带 @ScheduledTask 的那一组 bean，不是容器里的全部")
    void queriesOnlyAnnotatedBeans() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> annotated = new LinkedHashMap<>();
        annotated.put("testTask", new Object());
        annotated.put("scheduleJobLogCleanupTask", new Object());
        when(ctx.getBeansWithAnnotation(eq(ScheduledTask.class))).thenReturn(annotated);

        R r = controller(ctx).beans();

        assertEquals(0, r.get("code"));
        // 关键：必须是按注解查。改成 getBeanDefinitionNames() 之类的全量查询，
        // 会把容器里所有 bean 都列出来 —— 那是这个接口最不该做的事。
        verify(ctx).getBeansWithAnnotation(eq(ScheduledTask.class));

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) r.get("data");
        assertEquals(2, names.size(), "返回的数量应当和带注解的 bean 一致");
        assertTrue(names.contains("testTask"));
        assertTrue(names.contains("scheduleJobLogCleanupTask"));
    }

    @Test
    @DisplayName("返回顺序是稳定的 —— 容器扫描顺序变了下拉不该跟着抖")
    void namesAreSorted() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        Map<String, Object> annotated = new LinkedHashMap<>();
        // 故意用「反」序放进去
        annotated.put("zTask", new Object());
        annotated.put("aTask", new Object());
        annotated.put("mTask", new Object());
        when(ctx.getBeansWithAnnotation(eq(ScheduledTask.class))).thenReturn(annotated);

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) controller(ctx).beans().get("data");

        assertEquals(List.of("aTask", "mTask", "zTask"), names);
    }

    @Test
    @DisplayName("一个带注解的 bean 都没有时返回空列表，不是报错")
    void emptyIsNotAnError() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(eq(ScheduledTask.class))).thenReturn(Map.of());

        R r = controller(ctx).beans();

        assertEquals(0, r.get("code"), "没有可用任务不是异常状态，界面上显示一个空下拉即可");
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) r.get("data");
        assertTrue(names.isEmpty());
    }

    /**
     * 反向对照：确认这些断言真的在检查东西。
     *
     * 如果 getBeansWithAnnotation 的桩没配对，mock 会返回 null，
     * controller 里 keySet() 会 NPE —— 那样测试是「错误」而不是「失败」，
     * 看起来像环境问题而不是断言不成立。这里把那种情况和真正的空结果区分开。
     */
    @Test
    @DisplayName("反向对照：空结果和「桩没配对」是两回事")
    void emptyIsDistinctFromUnstubbed() {
        ApplicationContext stubbed = mock(ApplicationContext.class);
        when(stubbed.getBeansWithAnnotation(eq(ScheduledTask.class))).thenReturn(Map.of());
        @SuppressWarnings("unchecked")
        List<String> fromStub = (List<String>) controller(stubbed).beans().get("data");
        assertTrue(fromStub.isEmpty());

        // 没打桩的 mock：Mockito 对返回 Map 的方法默认返回空 Map 而不是 null，
        // 所以这里也能跑通。断言这一点，是为了让上面那条测试的「通过」有意义 ——
        // 如果哪天默认行为变成 null，这条会先炸，提示去修桩而不是怀疑实现。
        ApplicationContext bare = mock(ApplicationContext.class);
        @SuppressWarnings("unchecked")
        List<String> fromBare = (List<String>) controller(bare).beans().get("data");
        assertFalse(fromBare == null, "默认返回值变成 null 了，上面的测试需要显式打桩");
    }
}

package com.mall.admin;

import com.mall.admin.schedule.ScheduledTask;
import com.mall.admin.task.ScheduleJobLogCleanupTask;
import com.mall.admin.task.TestTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 守住定时任务的白名单机制。
 *
 * <h3>为什么这条比一般的功能测试要紧</h3>
 * renren 原版的定时任务是「填一个 bean 名 + 一个字符串，到点反射调它的 run 方法」——
 * <b>任何能创建定时任务的人都能调用容器里任意一个 bean 的方法</b>。
 * 后台账号被拿到、或者存在一个能创建任务的越权入口，就等于拿到了任意方法执行，
 * 而这一步不会留下任何异常，看起来就是一个正常的定时任务在跑。
 *
 * <p>白名单（{@link ScheduledTask}）是这次唯一的安全性改动。它很容易被「简化」掉 ——
 * 下一个人觉得「每个任务都要打个注解好麻烦」就删了，而删掉之后
 * <b>所有功能测试仍然全绿</b>：任务照样能跑，只是顺带把整个容器暴露了。
 * 所以必须有一条测试专门盯着它。
 *
 * <h3>为什么不起 Spring 上下文</h3>
 * mall-admin 的上下文要 MySQL（Quartz 的 JDBC 存储也要），本机没有。
 * 这里验的是「约定本身」—— 注解在不在、方法签名对不对、白名单里有哪些类，
 * 都是编译产物上的静态事实，不需要运行时。
 */
class ScheduledTaskWhitelistTest {

    private static final String TASK_PACKAGE = "com.mall.admin.task";

    /** 扫出所有标了 @ScheduledTask 的类。 */
    private static List<Class<?>> whitelistedTasks() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ScheduledTask.class));
        List<Class<?>> found = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.mall.admin")) {
            try {
                found.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
        }
        return found;
    }

    @Test
    @DisplayName("扫描本身有效：至少能扫到一个被白名单允许的任务")
    void scannerActuallyFindsTasks() {
        // 阳性对照。扫不到任何东西时，下面那条「每个任务都有 run(String)」
        // 会因为集合为空而恒真 —— 一个什么都没检查的测试永远是绿的。
        assertThat(whitelistedTasks())
                .as("一个 @ScheduledTask 都没扫到，说明扫描逻辑坏了（包名或过滤器），"
                        + "而不是真的没有任务")
                .isNotEmpty()
                // 列举【已知的】任务而不是断言个数：加新任务时不用改这条，
                // 但任何一个已知任务被扫描漏掉（改了包名、注解被摘掉）会立刻暴露。
                .contains(TestTask.class, ScheduleJobLogCleanupTask.class);
    }

    @Test
    @DisplayName("每个白名单任务都必须有 public void run(String)")
    void everyWhitelistedTaskHasTheAgreedMethod() {
        for (Class<?> type : whitelistedTasks()) {
            Method run;
            try {
                run = type.getDeclaredMethod("run", String.class);
            } catch (NoSuchMethodException e) {
                throw new AssertionError(type.getName()
                        + " 标了 @ScheduledTask 但没有 run(String) 方法。"
                        + "签名是固定的 —— 否则白名单只限制了类，类里的任意 public 方法仍然可达。", e);
            }
            assertThat(run.getReturnType())
                    .as("%s 的 run 返回值不是 void", type.getName())
                    .isEqualTo(void.class);
            assertThat(java.lang.reflect.Modifier.isPublic(run.getModifiers()))
                    .as("%s 的 run 不是 public", type.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("白名单只包含 task 包下的类 —— 防止有人给业务 service 打上注解")
    void whitelistStaysInTheTaskPackage() {
        // 定时任务应该是专门写的、只做编排的薄类。给一个业务 service 直接打上
        // @ScheduledTask 会把它的 run 方法变成可被外部触发的入口，
        // 而那个 service 通常还持有事务和更大的权限。
        // 约束在包上，是因为「哪些类算任务」这件事一眼可查比逐个 review 可靠。
        for (Class<?> type : whitelistedTasks()) {
            assertThat(type.getPackageName())
                    .as("%s 标了 @ScheduledTask 但不在 %s 包下。定时任务要单独写一个薄类，"
                            + "不要直接把业务 service 变成可被外部触发的入口。",
                            type.getName(), TASK_PACKAGE)
                    .isEqualTo(TASK_PACKAGE);
        }
    }

    @Test
    @DisplayName("@ScheduledTask 必须是运行时可见的 —— 否则执行时那道闸门形同虚设")
    void annotationIsRuntimeVisible() {
        // 执行器是用 isAnnotationPresent 在运行时判断的。如果注解的
        // @Retention 被改成 CLASS 或 SOURCE，那个判断会对【所有】类返回 false，
        // 结果是所有任务都执行不了 —— 而这个失败发生在下一个 cron 时间点，
        // 不是在部署的时候，很难关联回这次改动。
        java.lang.annotation.Retention retention =
                ScheduledTask.class.getAnnotation(java.lang.annotation.Retention.class);
        assertThat(retention).as("@ScheduledTask 没有 @Retention").isNotNull();
        assertThat(retention.value())
                .as("@ScheduledTask 的保留策略必须是 RUNTIME，执行器要在运行时读它")
                .isEqualTo(java.lang.annotation.RetentionPolicy.RUNTIME);
    }

    @Test
    @DisplayName("示例任务不碰数据 —— 它是给人拿来试手的")
    void sampleTaskDoesNotTouchData() {
        // TestTask 的用途是验证「调度器 -> 找到 bean -> 调用 -> 落执行日志」这条链路通不通。
        // 一个会改数据的示例任务，在别人拿它试手（尤其是点"立即执行"）的时候就成了事故。
        // 这里用「它不依赖任何东西」来近似这条约束：没有构造参数、没有字段注入。
        assertThat(TestTask.class.getDeclaredConstructors())
                .as("TestTask 不该有带参构造 —— 有依赖就说明它在碰别的东西")
                .hasSize(1);
        assertThat(TestTask.class.getDeclaredConstructors()[0].getParameterCount())
                .as("TestTask 不该有构造参数")
                .isZero();
        Set<String> allowedFields = Set.of("log");
        for (java.lang.reflect.Field field : TestTask.class.getDeclaredFields()) {
            assertThat(allowedFields)
                    .as("TestTask 多了一个字段 %s —— 示例任务应该只打日志，不持有任何依赖",
                            field.getName())
                    .contains(field.getName());
        }
    }

    @Test
    @DisplayName("没标注解的类不在白名单里（反向确认）")
    void unannotatedClassesAreNotWhitelisted() {
        // 反向对照：拿一个明确没标注解的类，确认扫描不会把它算进来。
        // 没有这条的话，「白名单里都是对的」可能只是因为扫描器把所有类都放进来了。
        assertThat(whitelistedTasks())
                .as("没标 @ScheduledTask 的类被算进了白名单，扫描器的过滤器没起作用")
                .doesNotContain(ScheduledTaskWhitelistTest.class, String.class);

        assertThatThrownBy(() -> {
            Class<?> notATask = ScheduledTaskWhitelistTest.class;
            if (!notATask.isAnnotationPresent(ScheduledTask.class)) {
                throw new IllegalArgumentException("bean 没有标 @ScheduledTask，不允许作为定时任务");
            }
        })
                .as("这条模拟的是 ScheduleJobService.assertBeanIsAllowed 的判断")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@ScheduledTask");
    }
}

package com.mall.admin.task;

import com.mall.admin.schedule.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例定时任务，也是「怎么写一个定时任务」的参照实现。
 *
 * <h3>为什么保留这个名字</h3>
 * {@code schedule_job_log} 里有 244 条历史记录，bean_name 全是 {@code testTask} ——
 * 旧 renren 调度器每半小时跑它一次，一直到 2026-08-25（那个模块被删的日子）。
 * 沿用同一个 bean 名，那些历史日志点开时仍然对得上一个真实存在的任务；
 * 前端新建任务表单里的占位提示写的也是 {@code 如: testTask}。
 *
 * <h3>写一个新任务要满足三件事</h3>
 * <ol>
 *   <li>标 {@link ScheduledTask}。不标的话保存时就会被拒 ——
 *       这是有意的白名单，见那个注解的说明（不加白名单的话，能创建定时任务
 *       就等于能调用容器里任意 bean 的方法）。</li>
 *   <li>有一个 {@code public void run(String params)}。签名固定，
 *       所以白名单限制的不只是类、还有方法。</li>
 *   <li>是一个 Spring bean，且 bean 名就是后台里要填的名字
 *       （{@code @Component} 默认取类名首字母小写，即 {@code testTask}）。</li>
 * </ol>
 *
 * <h3>任务实现自己要注意的</h3>
 * 执行器加了 {@code @DisallowConcurrentExecution}，所以同一个任务不会被并发触发。
 * 但<b>跨任务</b>没有互斥，而且 misfire 策略是「错过就跳过、不补跑」——
 * 需要「一天必须结算一次」这类保证的任务，要自己按日期做幂等，
 * 不能依赖调度器把错过的补回来。
 */
@ScheduledTask("示例任务：只打一条日志，用来验证调度链路是通的")
@Component("testTask")
public class TestTask {

    private static final Logger log = LoggerFactory.getLogger(TestTask.class);

    public void run(String params) {
        // 刻意只打日志、不碰任何数据：这个任务的用途是验证「调度器 -> 找到 bean ->
        // 调用 -> 落执行日志」这条链路是通的。一个会改数据的示例任务，
        // 在别人拿它试手的时候就成了事故。
        log.info("testTask 执行，参数={}", params);
    }
}

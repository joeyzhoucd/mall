package com.mall.admin.schedule;

import com.mall.admin.entity.ScheduleJobEntity;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.context.ApplicationContext;

/**
 * Quartz 触发器的增删改查。把「数据库里的一行任务」翻译成「调度器里的一个触发器」。
 *
 * <h3>为什么每个操作都先删后建，而不是改</h3>
 * Quartz 的 {@code rescheduleJob} 只能换触发器，换不了 JobDataMap 里的内容。
 * 而我们把整个任务实体塞在 JobDataMap 里（执行时要读 params），改 params 就必须重建。
 * 统一走「删掉再建」比「有时改有时建」少一个分支，也少一类
 * 「改了 cron 但 params 还是旧的」这种半更新状态。
 *
 * <h3>misfire 策略选 DoNothing 的理由</h3>
 * 默认策略（{@code SmartPolicy}）会把错过的触发<b>补跑</b>。
 * 一次滚动更新 pod 重启二十多秒，之后所有分钟级任务会一起补跑，形成一个尖峰；
 * 对「每分钟对账」这类任务，补跑五次和跑一次的效果是一样的，纯浪费。
 * DoNothing = 错过就跳过，等下一个正常时间点。
 * <p>
 * 需要「一次都不能少」的任务（比如按天结算）不适用这个策略 —— 那种任务应该
 * 自己按日期幂等，而不是依赖调度器补跑。
 */
public final class ScheduleUtils {

    /** JobDataMap 里存任务实体的键。 */
    public static final String JOB_PARAM_KEY = "MALL_SCHEDULE_JOB";

    /** JobDataMap 里存 ApplicationContext 的键，执行时要用它按名字取 bean。 */
    public static final String APPLICATION_CONTEXT_KEY = "MALL_APPLICATION_CONTEXT";

    private static final String JOB_NAME_PREFIX = "MALL_TASK_";

    private ScheduleUtils() {
    }

    public static TriggerKey triggerKey(Long jobId) {
        return TriggerKey.triggerKey(JOB_NAME_PREFIX + jobId);
    }

    public static JobKey jobKey(Long jobId) {
        return JobKey.jobKey(JOB_NAME_PREFIX + jobId);
    }

    /**
     * 建一个任务。已存在时先删掉重建（见类注释）。
     *
     * @throws IllegalArgumentException cron 表达式不合法 —— 这种错必须在这里抛出去让接口返回，
     *                                  不能吞掉：任务"保存成功了但没被调度"是最难发现的一种失败。
     */
    public static void createJob(Scheduler scheduler, ApplicationContext applicationContext,
                                 ScheduleJobEntity job) {
        try {
            // 先删干净。可能残留：上一版任务、或者旧 renren 调度器留在 QRTZ_TRIGGERS 里的行。
            deleteJob(scheduler, job.getJobId());

            JobDetail jobDetail = JobBuilder.newJob(ScheduleJobExecutor.class)
                    .withIdentity(jobKey(job.getJobId()))
                    .build();
            jobDetail.getJobDataMap().put(JOB_PARAM_KEY, job);
            jobDetail.getJobDataMap().put(APPLICATION_CONTEXT_KEY, applicationContext);

            CronScheduleBuilder schedule = CronScheduleBuilder
                    .cronSchedule(job.getCronExpression())
                    // 见类注释：错过就跳过，不补跑。
                    .withMisfireHandlingInstructionDoNothing();

            CronTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey(job.getJobId()))
                    .withSchedule(schedule)
                    .build();

            scheduler.scheduleJob(jobDetail, trigger);

            // 建完如果本来是暂停状态，立刻暂停。顺序不能反 ——
            // 先暂停再建的话，建的动作会把它恢复成运行。
            if (job.getStatus() != null && job.getStatus() == ScheduleJobEntity.STATUS_PAUSE) {
                pauseJob(scheduler, job.getJobId());
            }
        } catch (RuntimeException e) {
            // CronScheduleBuilder 对不合法的表达式抛的是 RuntimeException，
            // 单独接出来给一句能看懂的话 —— 原始异常信息是 "CronExpression '...' is invalid."，
            // 不说哪里不合法。
            throw new IllegalArgumentException("cron 表达式不合法: " + job.getCronExpression(), e);
        } catch (SchedulerException e) {
            throw new IllegalStateException("创建定时任务失败 jobId=" + job.getJobId(), e);
        }
    }

    public static void updateJob(Scheduler scheduler, ApplicationContext applicationContext,
                                 ScheduleJobEntity job) {
        // 见类注释：统一走删了重建，不用 rescheduleJob（它换不了 JobDataMap）。
        createJob(scheduler, applicationContext, job);
    }

    /** 立即执行一次。不影响原来的 cron 计划。 */
    public static void run(Scheduler scheduler, ScheduleJobEntity job) {
        try {
            org.quartz.JobDataMap dataMap = new org.quartz.JobDataMap();
            dataMap.put(JOB_PARAM_KEY, job);
            // triggerJob 用的是【已存在的 JobDetail】，所以 ApplicationContext 已经在
            // 它自己的 JobDataMap 里了；这里只覆盖任务实体，让手动执行用的是最新的 params。
            scheduler.triggerJob(jobKey(job.getJobId()), dataMap);
        } catch (SchedulerException e) {
            throw new IllegalStateException("立即执行定时任务失败 jobId=" + job.getJobId(), e);
        }
    }

    public static void pauseJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.pauseJob(jobKey(jobId));
        } catch (SchedulerException e) {
            throw new IllegalStateException("暂停定时任务失败 jobId=" + jobId, e);
        }
    }

    public static void resumeJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.resumeJob(jobKey(jobId));
        } catch (SchedulerException e) {
            throw new IllegalStateException("恢复定时任务失败 jobId=" + jobId, e);
        }
    }

    /** 删除。对不存在的 job 是幂等的（deleteJob 返回 false，不抛）。 */
    public static void deleteJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.deleteJob(jobKey(jobId));
        } catch (SchedulerException e) {
            throw new IllegalStateException("删除定时任务失败 jobId=" + jobId, e);
        }
    }
}

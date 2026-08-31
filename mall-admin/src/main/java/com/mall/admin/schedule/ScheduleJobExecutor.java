package com.mall.admin.schedule;

import com.mall.admin.dao.ScheduleJobLogDao;
import com.mall.admin.entity.ScheduleJobEntity;
import com.mall.admin.entity.ScheduleJobLogEntity;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * 到点执行一个任务：按 bean 名找到 Spring bean，调它的 {@code run(String)}，并落一条执行日志。
 *
 * <h3>@DisallowConcurrentExecution 为什么必须加</h3>
 * 没有它的话，一个执行时间超过间隔的任务会<b>被并发触发</b>：
 * 比如 cron 是每分钟、而某次执行花了三分钟，那三分钟里会累积起三个并发实例。
 * 对「对账」「清理」这类任务，并发跑同一批数据就是数据损坏。
 * 加上之后 Quartz 会等上一次跑完，跳过的那几次按 misfire 策略处理。
 *
 * <h3>为什么不用注入而用 ApplicationContext 现查</h3>
 * 任务的 bean 名是<b>运行时</b>从数据库读出来的字符串，编译期不知道要注入什么。
 * 这也是这套设计的代价：bean 名写错要到执行时才发现。所以下面把「找不到 bean」
 * 和「bean 没标注解」都写成明确的日志和失败记录，而不是让它静默什么也不做 ——
 * 一个「没报错但也没跑」的定时任务是最难查的。
 *
 * <h3>白名单检查在这里，不在保存的时候</h3>
 * 保存时也检查（见 ScheduleJobService），但这里<b>再查一遍</b>。
 * 原因：任务行是数据，可以被绕过接口直接写进数据库（比如从别的地方跑一条 SQL）。
 * 真正决定「能不能被调用」的是执行这一刻，所以最后一道闸门必须在这里。
 */
@DisallowConcurrentExecution
public class ScheduleJobExecutor extends QuartzJobBean {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobExecutor.class);

    /** 表里 error 列是 varchar(2000)，超了整条日志会写不进去，所以要截断。 */
    private static final int ERROR_MAX_LENGTH = 1900;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        ScheduleJobEntity job = (ScheduleJobEntity) context.getMergedJobDataMap()
                .get(ScheduleUtils.JOB_PARAM_KEY);
        ApplicationContext applicationContext = (ApplicationContext) context.getMergedJobDataMap()
                .get(ScheduleUtils.APPLICATION_CONTEXT_KEY);

        ScheduleJobLogEntity logEntity = new ScheduleJobLogEntity();
        logEntity.setJobId(job.getJobId());
        logEntity.setBeanName(job.getBeanName());
        logEntity.setParams(job.getParams());
        logEntity.setCreateTime(new Date());

        long start = System.currentTimeMillis();
        try {
            Object target = resolveTask(applicationContext, job.getBeanName());
            Method method = target.getClass().getDeclaredMethod("run", String.class);
            ReflectionUtils.makeAccessible(method);
            method.invoke(target, job.getParams());

            logEntity.setStatus(ScheduleJobLogEntity.STATUS_SUCCESS);
            logEntity.setTimes((int) (System.currentTimeMillis() - start));
            log.info("定时任务执行成功 jobId={} bean={} 耗时={}ms",
                    job.getJobId(), job.getBeanName(), logEntity.getTimes());
        } catch (Exception e) {
            logEntity.setStatus(ScheduleJobLogEntity.STATUS_FAIL);
            logEntity.setTimes((int) (System.currentTimeMillis() - start));
            String message = e.getCause() != null ? e.getCause().toString() : e.toString();
            logEntity.setError(message.length() > ERROR_MAX_LENGTH
                    ? message.substring(0, ERROR_MAX_LENGTH) : message);
            log.error("定时任务执行失败 jobId={} bean={}", job.getJobId(), job.getBeanName(), e);
        } finally {
            // 日志一定要落，包括失败的。执行失败还不留记录的话，后台看到的是
            // 「这个任务好像没跑」，而实际是跑了并且报错了 —— 两种情况的排查方向完全不同。
            try {
                applicationContext.getBean(ScheduleJobLogDao.class).insert(logEntity);
            } catch (Exception e) {
                log.error("写定时任务日志失败 jobId={}", job.getJobId(), e);
            }
        }
    }

    /**
     * 按名字取 bean，并校验它确实是被允许调用的任务。
     *
     * @throws IllegalStateException bean 不存在、没标 {@link ScheduledTask}、或没有 run(String) 方法
     */
    private Object resolveTask(ApplicationContext applicationContext, String beanName) {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalStateException("beanName 为空");
        }
        Object target;
        try {
            target = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new IllegalStateException("找不到名为 " + beanName + " 的 Spring bean", e);
        }
        // 白名单：只有标了 @ScheduledTask 的类才可以被定时任务调用。
        // 见 ScheduledTask 的注释 —— renren 原版允许调任意 bean，等于任意方法执行。
        // 注意用 AopUtils 拿目标类：被代理的 bean（@Transactional 等）getClass()
        // 拿到的是代理类，代理类上没有原类的注解。
        Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(target);
        if (!targetClass.isAnnotationPresent(ScheduledTask.class)) {
            throw new IllegalStateException("bean " + beanName + "（" + targetClass.getName()
                    + "）没有标 @ScheduledTask，不允许被定时任务调用");
        }
        return target;
    }
}

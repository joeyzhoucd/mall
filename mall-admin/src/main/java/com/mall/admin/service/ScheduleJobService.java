package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.ScheduleJobDao;
import com.mall.admin.entity.ScheduleJobEntity;
import com.mall.admin.schedule.ScheduleUtils;
import com.mall.admin.schedule.ScheduledTask;
import jakarta.annotation.PostConstruct;
import org.quartz.Scheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/** 定时任务的增删改与调度。 */
@Service
public class ScheduleJobService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobService.class);

    private final ScheduleJobDao scheduleJobDao;
    private final Scheduler scheduler;
    private final ApplicationContext applicationContext;

    public ScheduleJobService(ScheduleJobDao scheduleJobDao, Scheduler scheduler,
                              ApplicationContext applicationContext) {
        this.scheduleJobDao = scheduleJobDao;
        this.scheduler = scheduler;
        this.applicationContext = applicationContext;
    }

    /**
     * 启动时把数据库里的任务装进调度器。
     *
     * <h3>为什么需要这一步</h3>
     * Quartz 的 JDBC 存储里已经有触发器，理论上重启后会自己恢复。但数据库里的
     * schedule_job 才是<b>唯一依据</b>：任务行可能被直接改过（改 cron、改 params），
     * 而那些改动不会自动同步到 QRTZ_* 表。启动时按 schedule_job 重建一遍，
     * 保证「界面上看到的」和「实际在跑的」一致。
     *
     * <p>还有一个现实原因：QRTZ_* 表里有<b>旧 renren 调度器留下的残留</b>
     * （QRTZ_TRIGGERS 1 行、QRTZ_SCHEDULER_STATE 2 行，schedule_job_log 里最后一次
     * 执行是 2026-08-25 的 testTask）。那些触发器指向的 Job 类已经不存在了，
     * 留着只会在每次 checkin 时产生噪声。createJob 会先 deleteJob 再建，顺带清掉。
     *
     * <h3>单条失败不影响其余</h3>
     * 一个 cron 表达式写坏了不该让整个服务起不来 —— 那会把「一个任务配错」
     * 放大成「后台整个不可用」。所以逐条 try/catch 并打日志。
     */
    @PostConstruct
    public void initScheduledJobs() {
        List<ScheduleJobEntity> jobs = scheduleJobDao.selectList(new QueryWrapper<>());
        int ok = 0;
        for (ScheduleJobEntity job : jobs) {
            try {
                ScheduleUtils.createJob(scheduler, applicationContext, job);
                ok++;
            } catch (Exception e) {
                log.error("装载定时任务失败，已跳过 jobId={} bean={} cron={}: {}",
                        job.getJobId(), job.getBeanName(), job.getCronExpression(), e.getMessage());
            }
        }
        log.info("定时任务装载完成：共 {} 条，成功 {} 条", jobs.size(), ok);
    }

    public IPage<ScheduleJobEntity> page(int page, int limit, String beanName) {
        QueryWrapper<ScheduleJobEntity> wrapper = new QueryWrapper<>();
        if (beanName != null && !beanName.isBlank()) {
            wrapper.like("bean_name", beanName);
        }
        wrapper.orderByAsc("job_id");
        return scheduleJobDao.selectPage(new Page<>(page, limit), wrapper);
    }

    public ScheduleJobEntity info(Long jobId) {
        return scheduleJobDao.selectById(jobId);
    }

    /**
     * 新增。数据库和调度器要一起成功。
     *
     * <p>先写库拿到自增 jobId（调度器里的 key 用它），再建触发器；建触发器失败时
     * 抛异常让事务回滚，不留下「库里有这条任务但从来不会触发」的记录 ——
     * 那种记录在界面上看起来完全正常。
     */
    @Transactional
    public void save(ScheduleJobEntity job) {
        assertBeanIsAllowed(job.getBeanName());
        job.setCreateTime(new Date());
        job.setStatus(ScheduleJobEntity.STATUS_NORMAL);
        scheduleJobDao.insert(job);
        ScheduleUtils.createJob(scheduler, applicationContext, job);
    }

    @Transactional
    public void update(ScheduleJobEntity job) {
        assertBeanIsAllowed(job.getBeanName());
        ScheduleUtils.updateJob(scheduler, applicationContext, job);
        scheduleJobDao.updateById(job);
    }

    /**
     * 删除。先删调度器再删库 —— 和 sys_oss 删对象同一个道理：反过来的话
     * 触发器删失败时任务行已经没了，那个触发器就成了孤儿，到点还会触发，
     * 而且没人知道它对应哪条配置。
     */
    @Transactional
    public void delete(List<Long> jobIds) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        for (Long jobId : jobIds) {
            ScheduleUtils.deleteJob(scheduler, jobId);
        }
        scheduleJobDao.deleteByIds(jobIds);
    }

    /** 立即执行一次，不影响原来的 cron 计划。 */
    public void run(List<Long> jobIds) {
        forEach(jobIds, job -> ScheduleUtils.run(scheduler, job));
    }

    @Transactional
    public void pause(List<Long> jobIds) {
        forEach(jobIds, job -> {
            ScheduleUtils.pauseJob(scheduler, job.getJobId());
            job.setStatus(ScheduleJobEntity.STATUS_PAUSE);
            scheduleJobDao.updateById(job);
        });
    }

    @Transactional
    public void resume(List<Long> jobIds) {
        forEach(jobIds, job -> {
            ScheduleUtils.resumeJob(scheduler, job.getJobId());
            job.setStatus(ScheduleJobEntity.STATUS_NORMAL);
            scheduleJobDao.updateById(job);
        });
    }

    private void forEach(List<Long> jobIds, Consumer<ScheduleJobEntity> action) {
        if (jobIds == null || jobIds.isEmpty()) {
            return;
        }
        for (Long jobId : jobIds) {
            ScheduleJobEntity job = scheduleJobDao.selectById(jobId);
            // 找不到就跳过而不是抛：前端可能勾选了刚被别人删掉的行，
            // 为此让整批操作失败没有意义。
            if (job == null) {
                log.warn("定时任务不存在，已跳过 jobId={}", jobId);
                continue;
            }
            action.accept(job);
        }
    }

    /**
     * 保存前就检查 bean 是否允许被调用，让错误在<b>点保存的时候</b>就报出来。
     *
     * <p>执行时（ScheduleJobExecutor）还会再查一遍 —— 那不是重复：任务行是数据，
     * 可以绕过接口直接写进数据库。真正决定「能不能被调用」的是执行那一刻，
     * 所以最后一道闸门必须在执行处。这里这一道只是为了体验：否则用户要等到
     * 下一个 cron 时间点、再去翻执行日志才知道 bean 名写错了。
     */
    private void assertBeanIsAllowed(String beanName) {
        if (beanName == null || beanName.isBlank()) {
            throw new IllegalArgumentException("bean 名称不能为空");
        }
        Object bean;
        try {
            bean = applicationContext.getBean(beanName);
        } catch (Exception e) {
            throw new IllegalArgumentException("找不到名为 " + beanName + " 的 Spring bean");
        }
        // 用 AopUtils 拿目标类：被代理的 bean（@Transactional 等）getClass()
        // 拿到的是代理类，代理类上没有原类的注解。
        Class<?> targetClass = AopUtils.getTargetClass(bean);
        if (!targetClass.isAnnotationPresent(ScheduledTask.class)) {
            throw new IllegalArgumentException("bean " + beanName
                    + " 没有标 @ScheduledTask，不允许作为定时任务。"
                    + "这是有意的限制：不加白名单的话，能创建定时任务就等于能调用容器里任意 bean 的方法。");
        }
    }
}

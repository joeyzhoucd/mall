package com.mall.admin.controller;

import com.mall.admin.entity.ScheduleJobEntity;
import com.mall.admin.service.ScheduleJobService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务管理。
 *
 * <h3>为什么是 Quartz，而不是 @Scheduled + ShedLock</h3>
 * 后台要能<b>动态</b>增删任务、改 cron、暂停/恢复、立即执行一次。
 * {@code @Scheduled} 是编译期固定的注解，这些一个都做不到。
 * 而 Quartz 的 JDBC 存储天然是集群安全的：mall-admin 现在 1 个副本，
 * 扩到多副本时同一个任务不会被两个实例同时触发 ——
 * 那是 {@code @Scheduled} 那条路必须自己用分布式锁补的。
 *
 * <h3>一处对 renren 原实现的安全修正</h3>
 * renren 的模型是「填一个 bean 名 + 一个字符串参数，到点反射调它的 run 方法」，
 * 也就是<b>任何能创建定时任务的人都能调用容器里任意 bean 的方法</b>。
 * 这里加了白名单：只有标了 {@code @ScheduledTask} 的类才可能被调用，
 * 而且方法签名固定为 {@code public void run(String)}。
 * 保存时和执行时各查一遍 —— 执行时那一遍才是真闸门（任务行是数据，可以绕过接口写进库）。
 *
 * <h3>批量操作的语义</h3>
 * delete / run / pause / resume 都收一个 id 数组（前端单条操作也发数组）。
 * 其中 run/pause/resume 对<b>找不到的 id 跳过而不报错</b>：
 * 前端可能勾选了刚被别人删掉的行，为此让整批失败没有意义。
 */
@RestController
@RequestMapping("/sys/schedule")
public class SysScheduleController {

    private final ScheduleJobService scheduleJobService;

    public SysScheduleController(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "beanName", required = false) String beanName) {
        return R.ok().put("page", new PageUtils(scheduleJobService.page(page, limit, beanName)));
    }

    @GetMapping("/info/{jobId}")
    public R info(@PathVariable("jobId") Long jobId) {
        ScheduleJobEntity job = scheduleJobService.info(jobId);
        if (job == null) {
            return R.error("定时任务不存在: " + jobId);
        }
        return R.ok().put("schedule", job);
    }

    @PostMapping("/save")
    public R save(@RequestBody ScheduleJobEntity job) {
        try {
            // jobId 由数据库生成，不接受客户端传入 —— 否则可以覆盖已有任务。
            job.setJobId(null);
            scheduleJobService.save(job);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            return R.error("保存失败: " + e.getMessage());
        }
    }

    @PostMapping("/update")
    public R update(@RequestBody ScheduleJobEntity job) {
        if (job.getJobId() == null) {
            return R.error("jobId 不能为空");
        }
        try {
            scheduleJobService.update(job);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            return R.error("更新失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> jobIds) {
        try {
            scheduleJobService.delete(jobIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("删除失败: " + e.getMessage());
        }
    }

    /** 立即执行一次，不影响原来的 cron 计划。 */
    @PostMapping("/run")
    public R run(@RequestBody List<Long> jobIds) {
        try {
            scheduleJobService.run(jobIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("执行失败: " + e.getMessage());
        }
    }

    @PostMapping("/pause")
    public R pause(@RequestBody List<Long> jobIds) {
        try {
            scheduleJobService.pause(jobIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("暂停失败: " + e.getMessage());
        }
    }

    @PostMapping("/resume")
    public R resume(@RequestBody List<Long> jobIds) {
        try {
            scheduleJobService.resume(jobIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("恢复失败: " + e.getMessage());
        }
    }
}

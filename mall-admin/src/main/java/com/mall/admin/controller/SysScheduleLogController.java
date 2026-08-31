package com.mall.admin.controller;

import com.mall.admin.entity.ScheduleJobLogEntity;
import com.mall.admin.service.ScheduleJobLogService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 定时任务执行日志，只读。
 *
 * <p>路径是 {@code /sys/scheduleLog}（驼峰），不是 {@code /sys/schedule-log} ——
 * 前端 job/schedule-log.vue 调的就是这个，不改前端就得按它来。
 *
 * <p>没有删除接口，理由见 {@code ScheduleJobLogService} 的类注释：
 * 执行日志是排查「任务为什么没跑/跑错」的唯一线索，手动删除等于允许抹掉证据。
 */
@RestController
@RequestMapping("/sys/scheduleLog")
public class SysScheduleLogController {

    private final ScheduleJobLogService scheduleJobLogService;

    public SysScheduleLogController(ScheduleJobLogService scheduleJobLogService) {
        this.scheduleJobLogService = scheduleJobLogService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit,
                  @RequestParam(value = "jobId", required = false) Long jobId) {
        return R.ok().put("page", new PageUtils(scheduleJobLogService.page(page, limit, jobId)));
    }

    @GetMapping("/info/{logId}")
    public R info(@PathVariable("logId") Long logId) {
        ScheduleJobLogEntity logEntity = scheduleJobLogService.info(logId);
        if (logEntity == null) {
            return R.error("执行日志不存在: " + logId);
        }
        return R.ok().put("log", logEntity);
    }
}

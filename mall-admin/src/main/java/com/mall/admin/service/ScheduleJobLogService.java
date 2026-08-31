package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.ScheduleJobLogDao;
import com.mall.admin.entity.ScheduleJobLogEntity;
import org.springframework.stereotype.Service;

/**
 * 定时任务执行日志，只读。
 *
 * <p>没有删除接口。这张表会一直长（每次执行一行），迟早需要清理，但清理策略
 * 不该做成一个「后台点一下删掉」的按钮 —— 执行日志是排查任务为什么没跑/跑错的
 * 唯一线索，手动删除等于允许把证据抹掉。到时候用一个按保留天数归档的定时任务
 * （它自己也是一个 @ScheduledTask），比给界面加个删除按钮合适。
 */
@Service
public class ScheduleJobLogService {

    private final ScheduleJobLogDao scheduleJobLogDao;

    public ScheduleJobLogService(ScheduleJobLogDao scheduleJobLogDao) {
        this.scheduleJobLogDao = scheduleJobLogDao;
    }

    public IPage<ScheduleJobLogEntity> page(int page, int limit, Long jobId) {
        QueryWrapper<ScheduleJobLogEntity> wrapper = new QueryWrapper<>();
        if (jobId != null) {
            wrapper.eq("job_id", jobId);
        }
        // 最近的排前面：看日志的场景是「刚才那次跑成功了吗」，不是翻历史。
        wrapper.orderByDesc("log_id");
        return scheduleJobLogDao.selectPage(new Page<>(page, limit), wrapper);
    }

    public ScheduleJobLogEntity info(Long logId) {
        return scheduleJobLogDao.selectById(logId);
    }
}

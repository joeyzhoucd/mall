package com.mall.admin.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.admin.dao.ScheduleJobLogDao;
import com.mall.admin.entity.ScheduleJobLogEntity;
import com.mall.admin.schedule.ScheduledTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 按保留天数清理定时任务执行日志。
 *
 * <h3>为什么是一个定时任务，而不是界面上的删除按钮</h3>
 * {@code schedule_job_log} 每执行一次就长一行 —— 实测约 48 行/天
 * （那个半小时一次的 testTask）。加上分钟级任务的话一天上千行，迟早要清。
 * <p>
 * 但清理<b>不该做成「后台点一下删掉」</b>：执行日志是排查「任务为什么没跑 / 跑错了」的
 * 唯一线索，手动删除等于允许把证据抹掉。做成按天数归档的策略，
 * 删的是「已经过了保留期的」，不是「某人想让它消失的」。
 * 所以 {@code SysScheduleLogController} 里刻意没有删除接口。
 *
 * <h3>为什么按 log_id 删，不按 create_time 删</h3>
 * {@code schedule_job_log} 上<b>没有 create_time 的索引</b>（实测确认，只有主键和 job_id）。
 * 直接 {@code DELETE WHERE create_time < ?} 每一批都是全表扫 ——
 * 现在 244 行无所谓，长到几百万行时会把表锁很久，而且现在有从库，
 * 长事务会直接变成复制延迟。
 * <p>
 * 所以先用<b>一次</b>查询找到「比截止时间更早的最大 log_id」，然后按主键范围分批删。
 * 这依赖一个前提：{@code log_id} 是自增的，所以它和 {@code create_time} 单调同序。
 * 对日志表成立（只有顺序 insert）。如果哪天有人批量导入乱序的历史日志，
 * 这个前提就破了 —— 那时的表现是「少删了一些」，不是「删错了」，可以接受。
 *
 * <h3>为什么分批，以及为什么不加 @Transactional</h3>
 * 一条 {@code DELETE} 删掉几百万行会产生一个巨大的事务：undo log 撑爆、
 * 主从复制延迟、锁等待。分批（每批 1000）让每一批都是独立的短事务。
 * <b>刻意不加 {@code @Transactional}</b> —— 加了就把所有批次并成一个大事务，
 * 正好抵消了分批的意义。
 * <p>
 * 代价是中途失败会「删了一半」。对日志表这没有问题：下一次执行会接着删，
 * 而且删除本身是幂等的（按 id 范围删已经不存在的行是空操作）。
 *
 * <h3>参数</h3>
 * {@code params} 是保留天数，比如 {@code 30}。留空或填不出数字时用默认值 30 ——
 * 不是抛异常：一个定时任务因为参数没填而每次都失败，比用一个保守的默认值糟。
 * 但会打 WARN，否则「填错了」和「故意留空」看起来一样。
 *
 * <h3>怎么启用</h3>
 * 这个类本身不会自动跑 —— 要在后台「定时任务」里新建一条：
 * bean 名 {@code scheduleJobLogCleanupTask}，cron 比如 {@code 0 30 3 * * ?}（每天 3:30），
 * 参数填保留天数。
 * 刻意<b>不预置</b>这条任务：保留多久是运维决定，不该由代码替人定死。
 */
@ScheduledTask("清理超过保留期的定时任务执行日志")
@Component("scheduleJobLogCleanupTask")
public class ScheduleJobLogCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ScheduleJobLogCleanupTask.class);

    private static final int DEFAULT_RETAIN_DAYS = 30;

    /** 每批删多少行。见类注释：分批是为了避免长事务和复制延迟。 */
    private static final int BATCH_SIZE = 1000;

    /**
     * 安全上限：一次执行最多删多少批。
     *
     * <p>防的是「表异常地大」时这个任务一跑几个小时、把数据库压住。
     * 达到上限就停下、打日志，剩下的下次接着删 —— 比跑到底更可控。
     */
    private static final int MAX_BATCHES = 100;

    private final ScheduleJobLogDao scheduleJobLogDao;

    public ScheduleJobLogCleanupTask(ScheduleJobLogDao scheduleJobLogDao) {
        this.scheduleJobLogDao = scheduleJobLogDao;
    }

    public void run(String params) {
        int retainDays = parseRetainDays(params);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retainDays);

        // 一次查询定位边界：比 cutoff 更早的最大 log_id。
        // 之后全部按主键范围删，不再碰没有索引的 create_time。
        Long boundaryId = findMaxLogIdBefore(cutoff);
        if (boundaryId == null) {
            log.info("清理执行日志：保留 {} 天，没有早于 {} 的记录，无需清理", retainDays, cutoff);
            return;
        }

        int totalDeleted = 0;
        int batches = 0;
        while (batches < MAX_BATCHES) {
            // 按主键范围 + limit 删。每一批是独立的短事务（这个方法没有 @Transactional）。
            int deleted = scheduleJobLogDao.delete(new QueryWrapper<ScheduleJobLogEntity>()
                    .le("log_id", boundaryId)
                    .last("LIMIT " + BATCH_SIZE));
            batches++;
            totalDeleted += deleted;
            if (deleted < BATCH_SIZE) {
                break;
            }
        }

        if (batches >= MAX_BATCHES) {
            log.warn("清理执行日志：达到单次批次上限 {}（已删 {} 行），剩余部分下次继续。"
                            + "连续多次出现说明保留天数或任务频率需要调整",
                    MAX_BATCHES, totalDeleted);
        } else {
            log.info("清理执行日志：保留 {} 天，删除 {} 行（{} 批），边界 log_id={}",
                    retainDays, totalDeleted, batches, boundaryId);
        }
    }

    /**
     * @return 比 cutoff 更早的最大 log_id；一条都没有时返回 null
     */
    private Long findMaxLogIdBefore(LocalDateTime cutoff) {
        // 只取一行：按 create_time 过滤、按 log_id 倒序取第一条。
        // 这一次查询会扫 create_time（没索引），但只做一次，不是每批一次。
        List<ScheduleJobLogEntity> rows = scheduleJobLogDao.selectList(
                new QueryWrapper<ScheduleJobLogEntity>()
                        .select("log_id")
                        .lt("create_time", cutoff)
                        .orderByDesc("log_id")
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0).getLogId();
    }

    private int parseRetainDays(String params) {
        if (params == null || params.isBlank()) {
            log.warn("清理执行日志：未提供保留天数，使用默认值 {} 天", DEFAULT_RETAIN_DAYS);
            return DEFAULT_RETAIN_DAYS;
        }
        try {
            int days = Integer.parseInt(params.trim());
            if (days < 1) {
                // 不接受 0 或负数：那等于「全删」，而这个任务的定位是保留策略，
                // 不是清空工具。真要清空应该是一次明确的人工操作，不是配错一个参数的后果。
                log.warn("清理执行日志：保留天数 {} 不合法（必须 >= 1），使用默认值 {} 天",
                        days, DEFAULT_RETAIN_DAYS);
                return DEFAULT_RETAIN_DAYS;
            }
            return days;
        } catch (NumberFormatException e) {
            log.warn("清理执行日志：保留天数 \"{}\" 不是数字，使用默认值 {} 天",
                    params, DEFAULT_RETAIN_DAYS);
            return DEFAULT_RETAIN_DAYS;
        }
    }
}

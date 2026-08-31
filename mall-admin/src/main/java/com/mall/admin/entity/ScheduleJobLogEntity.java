package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * schedule_job_log —— 定时任务执行日志。
 *
 * <p>这张表里有 244 条历史记录，最后一条是 2026-08-25 的 testTask ——
 * 旧 renren 调度器一直跑到那天（那个模块被删的日子）。刻意<b>不清空</b>：
 * 它是这套系统真实跑过的证据，而且新实现写进来的行和它们结构完全一致。
 */
@Data
@TableName("schedule_job_log")
public class ScheduleJobLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行成功。 */
    public static final int STATUS_SUCCESS = 0;

    /** 执行失败。 */
    public static final int STATUS_FAIL = 1;

    @TableId
    private Long logId;

    private Long jobId;

    private String beanName;

    private String params;

    /** 0 成功，1 失败。 */
    private Integer status;

    /** 失败时的异常信息。表里是 varchar(2000)，写入前必须截断，否则整条日志写不进去。 */
    private String error;

    /** 耗时（毫秒）。字段名是 times，沿用旧表。 */
    private Integer times;

    private Date createTime;
}

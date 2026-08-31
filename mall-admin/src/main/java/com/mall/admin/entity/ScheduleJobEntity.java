package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * schedule_job —— 定时任务。
 *
 * <p>表结构沿用旧 renren 后台留下的那张（表已存在，没有重建）：
 * job_id / bean_name / params / cron_expression / status / remark / create_time。
 * 前端的列表列名就是这些驼峰形式，别改。
 */
@Data
@TableName("schedule_job")
public class ScheduleJobEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 正常运行。 */
    public static final int STATUS_NORMAL = 0;

    /** 已暂停。 */
    public static final int STATUS_PAUSE = 1;

    @TableId
    private Long jobId;

    /**
     * Spring bean 名。只能是标了 {@link com.mall.admin.schedule.ScheduledTask} 的 bean ——
     * 见那个注解的说明（renren 原版允许调任意 bean，等于任意方法执行）。
     */
    private String beanName;

    /** 传给 run(String) 的参数，就一个字符串。 */
    private String params;

    private String cronExpression;

    /** 0 正常，1 暂停。 */
    private Integer status;

    private String remark;

    private Date createTime;
}

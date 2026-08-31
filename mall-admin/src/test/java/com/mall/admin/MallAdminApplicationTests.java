package com.mall.admin;

import com.mall.testsupport.MallIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * 上下文启动的集成测试。本服务需要的容器：MySQL（health 里只有 db）。
 *
 * <h3>为什么用 AdminContainers 而不是共享的 Containers.Mysql</h3>
 * 共享容器起的是空库。加上定时任务之后 mall-admin <b>在启动时就要查表</b>
 * （{@code ScheduleJobService} 的 {@code @PostConstruct} 把数据库里的任务装进调度器），
 * 空库会让上下文起不来 —— CI 实测报 {@code Table 'mall_test.schedule_job' doesn't exist}。
 * {@link AdminContainers.MysqlWithSchema} 用 {@code withInitScript} 灌了那两张表。
 *
 * <h3>这个测试现在比原来值钱多了</h3>
 * 原来它只验证「Spring 能不能拼出一个上下文」。现在它还会真的：
 * <ul>
 *   <li>让 <b>Quartz 在真实 MySQL 上初始化 JDBC 存储</b>（建 11 张 QRTZ_* 表、
 *       启动集群模式的调度器、做第一次 checkin）；</li>
 *   <li>执行 {@code initScheduledJobs()}，也就是「读 schedule_job -> 建触发器」这条路径；</li>
 *   <li>触发 {@code EagerConnectionWarmup} 去真的开数据库连接。</li>
 * </ul>
 * 这几件事在本机都跑不了（没有 Docker），只有 CI 能验 —— 而它们恰好是最容易
 * 「编译通过、上线才炸」的部分。
 *
 * <h3>为什么这里的 quartz initialize-schema 是 always，而生产是 never</h3>
 * 生产环境的 QRTZ_* 表<b>已经存在</b>（旧 renren 后台留下的，
 * {@code schedule_job_log} 里还有 244 条历史执行记录）—— 设成 always 会在每次启动
 * 重建表结构，把那些历史一起清掉。
 * 而测试容器是全新的空库，必须让 Quartz 自己建。
 * 这个差异是<b>有意的</b>，不是配置漂移。
 */
@MallIntegrationTest
@Import(AdminContainers.MysqlWithSchema.class)
@TestPropertySource(properties = {
        // 见类注释：测试容器是空库，让 Quartz 自己建它那 11 张表。
        // 生产是 never，因为表已经存在且带着历史数据。
        "spring.quartz.jdbc.initialize-schema=always",
})
class MallAdminApplicationTests {

    @Test
    void contextLoads() {
    }
}

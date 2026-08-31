package com.mall.admin;

import com.mall.testsupport.TestImages;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * mall-admin 专用的 MySQL 容器：<b>带 schema</b>。
 *
 * <h3>为什么不用共享的 Containers.Mysql</h3>
 * 那个容器起的是一个空库，注释里也写着「这些测试只验证上下文能起来，不查表」。
 * 对其余 10 个模块成立，对 mall-admin <b>不成立</b>：
 * {@code ScheduleJobService} 的 {@code @PostConstruct} 在启动时就要查
 * {@code schedule_job}（把数据库里的任务装进调度器）。CI 上实测报的是
 * {@code Table 'mall_test.schedule_job' doesn't exist}，上下文直接起不来。
 *
 * <p>所以这个模块自己定义一个带 {@code withInitScript} 的容器，而不是去改共享的那个 ——
 * 把 mall-admin 的表塞进共享容器，等于让另外 10 个模块的测试也去建它们用不到的表。
 *
 * <h3>为什么用 withInitScript，而不是 spring.sql.init.schema-locations</h3>
 * 后者走 Spring 的数据库初始化器，它和 Quartz 的 {@code SchedulerFactoryBean}、
 * MyBatis 的 {@code SqlSessionFactory} 之间的先后要靠
 * {@code @DependsOnDatabaseInitialization} 保证，而 MyBatis 那边不一定标了 ——
 * 顺序不确定的测试会变成偶发失败，那比没有测试更糟。
 * {@code withInitScript} 在<b>容器启动时</b>执行，早于 Spring 连上来，顺序是确定的。
 *
 * <h3>QRTZ_* 那 11 张表不在脚本里</h3>
 * 让 Quartz 自己建：quartz jar 里带着
 * {@code org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql}，
 * 测试里把 {@code spring.quartz.jdbc.initialize-schema} 设成 {@code always} 就会执行它。
 * <p>
 * 手抄一份到 SQL 脚本里是错的：那份 DDL 会随 Quartz 版本变，抄下来之后就再也不会
 * 跟着升级，而不一致的表现是运行期某个字段找不到 —— 一个在版本升级后才爆的问题。
 * <p>
 * 注意生产环境是 {@code never}（表已经存在，是旧 renren 留下的，里面有 244 条历史日志），
 * 只有测试是 {@code always}。这个差异是有意的，写在
 * {@code MallAdminApplicationTests} 的 {@code @TestPropertySource} 上。
 */
public final class AdminContainers {

    private AdminContainers() {
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class MysqlWithSchema {

        @Bean
        @ServiceConnection
        MySQLContainer mysqlContainer() {
            return new MySQLContainer(TestImages.MYSQL)
                    .withDatabaseName("mall_test")
                    .withUsername("mall")
                    .withPassword("mall")
                    // 只建 mall-admin 启动时会访问的两张表，见 admin-test-schema.sql 的说明。
                    .withInitScript("schema/admin-test-schema.sql");
        }
    }
}

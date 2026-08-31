-- =============================================================================
-- 集成测试用的最小 schema：只建 mall-admin【启动时】会访问的表
-- =============================================================================
--
-- 为什么需要这个文件：
-- MallAdminApplicationTests 起的是一个空的 MySQL 容器，而 ScheduleJobService 的
-- @PostConstruct 在启动时就要查 schedule_job（把数据库里的任务装进调度器）。
-- 没有这张表，上下文直接起不来 —— CI 上实测报的是
--   Table 'mall_test.schedule_job' doesn't exist
--
-- 这也修正了 Containers.Mysql 注释里那条已经不成立的假设：
-- 「这些测试只验证上下文能起来，不查表」。mall-admin 会查。
--
-- 【只建这两张，不建 QRTZ_*】
-- Quartz 自己的 11 张表由它自己建：quartz jar 里带着
-- org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql，测试里把
-- spring.quartz.jdbc.initialize-schema 设成 always 就会执行它。
-- 手抄一份 Quartz 的 DDL 到这里是错的 —— 那份 DDL 会随 Quartz 版本变，
-- 抄下来之后就再也不会跟着升级，而不一致的表现是运行期某个字段找不到。
--
-- 【和生产的表结构逐字对齐】
-- 从线上 SHOW CREATE TABLE 抄的（去掉了 AUTO_INCREMENT 起始值和 collate）。
-- job_id / log_id 都是 AUTO_INCREMENT —— 这一条不能省：
-- ScheduleJobService.save() 先 insert 再读回 jobId 去建触发器，
-- 不自增的话拿到的是 null，触发器的 key 会变成 MALL_TASK_null。
--
-- 【为什么不是 spring.sql.init.schema-locations】
-- 那个走的是 Spring 的初始化器，和 Quartz 的 SchedulerFactoryBean、
-- MyBatis 的 SqlSessionFactory 之间的顺序要靠 @DependsOnDatabaseInitialization 保证，
-- 而 MyBatis 那边不一定标了。用容器的 withInitScript 更确定：
-- 脚本在容器启动时执行，早于 Spring 连上来。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `schedule_job` (
  `job_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务id',
  `bean_name`       VARCHAR(200)          DEFAULT NULL   COMMENT 'spring bean名称',
  `params`          VARCHAR(2000)         DEFAULT NULL   COMMENT '参数',
  `cron_expression` VARCHAR(100)          DEFAULT NULL   COMMENT 'cron表达式',
  `status`          TINYINT               DEFAULT NULL   COMMENT '任务状态 0：正常 1：暂停',
  `remark`          VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `create_time`     DATETIME              DEFAULT NULL   COMMENT '创建时间',
  PRIMARY KEY (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务';

CREATE TABLE IF NOT EXISTS `schedule_job_log` (
  `log_id`      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '任务日志id',
  `job_id`      BIGINT        NOT NULL                COMMENT '任务id',
  `bean_name`   VARCHAR(200)           DEFAULT NULL   COMMENT 'spring bean名称',
  `params`      VARCHAR(2000)          DEFAULT NULL   COMMENT '参数',
  `status`      TINYINT       NOT NULL                COMMENT '任务状态 0：成功 1：失败',
  `error`       VARCHAR(2000)          DEFAULT NULL   COMMENT '失败信息',
  `times`       INT           NOT NULL                COMMENT '耗时(单位：毫秒)',
  `create_time` DATETIME               DEFAULT NULL   COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  KEY `job_id` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时任务日志';

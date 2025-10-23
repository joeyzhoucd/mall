package io.renren.modules.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Schedule configuration
 */
@Configuration
public class ScheduleConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);

        // Quartz properties
        Properties prop = new Properties();
        prop.put("org.quartz.scheduler.instanceName", "RenrenScheduler");
        prop.put("org.quartz.scheduler.instanceId", "AUTO");
        // Thread pool configuration
        prop.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        prop.put("org.quartz.threadPool.threadCount", "20");
        prop.put("org.quartz.threadPool.threadPriority", "5");
        // JobStore configuration
        prop.put("org.quartz.jobStore.class", "org.springframework.scheduling.quartz.LocalDataSourceJobStore");
        // Cluster configuration
        prop.put("org.quartz.jobStore.isClustered", "true");
        prop.put("org.quartz.jobStore.clusterCheckinInterval", "15000");
        prop.put("org.quartz.jobStore.maxMisfiresToHandleAtATime", "1");

        prop.put("org.quartz.jobStore.misfireThreshold", "12000");
        prop.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        prop.put("org.quartz.jobStore.selectWithLockSQL", "SELECT * FROM {0}LOCKS UPDLOCK WHERE LOCK_NAME = ?");

        // PostgreSQL configuration
        //prop.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");

        factory.setQuartzProperties(prop);

        factory.setSchedulerName("RenrenScheduler");
        // Startup delay
        factory.setStartupDelay(30);
        factory.setApplicationContextSchedulerContextKey("applicationContextKey");
        // Overwrite existing jobs
        factory.setOverwriteExistingJobs(true);
        // Auto startup
        factory.setAutoStartup(true);

        return factory;
    }
}
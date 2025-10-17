/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * å®šæ—¶ä»»åŠ¡é…ç½®
 *
 * @author Mark sunlightcs@gmail.com
 */
@Configuration
public class ScheduleConfig {

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setDataSource(dataSource);

        //quartzå‚æ•°
        Properties prop = new Properties();
        prop.put("org.quartz.scheduler.instanceName", "RenrenScheduler");
        prop.put("org.quartz.scheduler.instanceId", "AUTO");
        //çº¿ç¨‹æ± é…ç½®
        prop.put("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        prop.put("org.quartz.threadPool.threadCount", "20");
        prop.put("org.quartz.threadPool.threadPriority", "5");
        //JobStoreé…ç½®
        prop.put("org.quartz.jobStore.class", "org.springframework.scheduling.quartz.LocalDataSourceJobStore");
        //é›†ç¾¤é…ç½®
        prop.put("org.quartz.jobStore.isClustered", "true");
        prop.put("org.quartz.jobStore.clusterCheckinInterval", "15000");
        prop.put("org.quartz.jobStore.maxMisfiresToHandleAtATime", "1");

        prop.put("org.quartz.jobStore.misfireThreshold", "12000");
        prop.put("org.quartz.jobStore.tablePrefix", "QRTZ_");
        prop.put("org.quartz.jobStore.selectWithLockSQL", "SELECT * FROM {0}LOCKS UPDLOCK WHERE LOCK_NAME = ?");

        //PostgreSQLæ•°æ®åº“ï¼Œéœ€è¦æ‰“å¼€æ­¤æ³¨é‡Š
        //prop.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");

        factory.setQuartzProperties(prop);

        factory.setSchedulerName("RenrenScheduler");
        //å»¶æ—¶å¯åŠ¨
        factory.setStartupDelay(30);
        factory.setApplicationContextSchedulerContextKey("applicationContextKey");
        //å¯é€‰ï¼ŒQuartzScheduler å¯åŠ¨æ—¶æ›´æ–°å·±å­˜åœ¨çš„Jobï¼Œè¿™æ ·å°±ä¸ç”¨æ¯æ¬¡ä¿®æ”¹targetObjectåŽåˆ é™¤qrtz_job_detailsè¡¨å¯¹åº”è®°å½•äº†
        factory.setOverwriteExistingJobs(true);
        //è®¾ç½®è‡ªåŠ¨å¯åŠ¨ï¼Œé»˜è®¤ä¸ºtrue
        factory.setAutoStartup(true);

        return factory;
    }
}

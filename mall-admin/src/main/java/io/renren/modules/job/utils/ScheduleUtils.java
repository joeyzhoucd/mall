/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.job.utils;

import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;
import io.renren.modules.job.entity.ScheduleJobEntity;
import org.quartz.*;

/**
 * å®šæ—¶ä»»åŠ¡å·¥å…·ç±»
 *
 * @author Mark sunlightcs@gmail.com
 */
public class ScheduleUtils {
    private final static String JOB_NAME = "TASK_";
    
    /**
     * èŽ·å–è§¦å‘å™¨key
     */
    public static TriggerKey getTriggerKey(Long jobId) {
        return TriggerKey.triggerKey(JOB_NAME + jobId);
    }
    
    /**
     * èŽ·å–jobKey
     */
    public static JobKey getJobKey(Long jobId) {
        return JobKey.jobKey(JOB_NAME + jobId);
    }

    /**
     * èŽ·å–è¡¨è¾¾å¼è§¦å‘å™¨
     */
    public static CronTrigger getCronTrigger(Scheduler scheduler, Long jobId) {
        try {
            return (CronTrigger) scheduler.getTrigger(getTriggerKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("èŽ·å–å®šæ—¶ä»»åŠ¡CronTriggerå‡ºçŽ°å¼‚å¸¸", e);
        }
    }

    /**
     * åˆ›å»ºå®šæ—¶ä»»åŠ¡
     */
    public static void createScheduleJob(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
        	//æž„å»ºjobä¿¡æ¯
            JobDetail jobDetail = JobBuilder.newJob(ScheduleJob.class).withIdentity(getJobKey(scheduleJob.getJobId())).build();

            //è¡¨è¾¾å¼è°ƒåº¦æž„å»ºå™¨
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            //æŒ‰æ–°çš„cronExpressionè¡¨è¾¾å¼æž„å»ºä¸€ä¸ªæ–°çš„trigger
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(getTriggerKey(scheduleJob.getJobId())).withSchedule(scheduleBuilder).build();

            //æ”¾å…¥å‚æ•°ï¼Œè¿è¡Œæ—¶çš„æ–¹æ³•å¯ä»¥èŽ·å–
            jobDetail.getJobDataMap().put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);

            scheduler.scheduleJob(jobDetail, trigger);
            
            //æš‚åœä»»åŠ¡
            if(scheduleJob.getStatus() == Constant.ScheduleStatus.PAUSE.getValue()){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }
        } catch (SchedulerException e) {
            throw new RRException("åˆ›å»ºå®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }
    
    /**
     * æ›´æ–°å®šæ—¶ä»»åŠ¡
     */
    public static void updateScheduleJob(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
            TriggerKey triggerKey = getTriggerKey(scheduleJob.getJobId());

            //è¡¨è¾¾å¼è°ƒåº¦æž„å»ºå™¨
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            CronTrigger trigger = getCronTrigger(scheduler, scheduleJob.getJobId());
            
            //æŒ‰æ–°çš„cronExpressionè¡¨è¾¾å¼é‡æ–°æž„å»ºtrigger
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();
            
            //å‚æ•°
            trigger.getJobDataMap().put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);
            
            scheduler.rescheduleJob(triggerKey, trigger);
            
            //æš‚åœä»»åŠ¡
            if(scheduleJob.getStatus() == Constant.ScheduleStatus.PAUSE.getValue()){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }
            
        } catch (SchedulerException e) {
            throw new RRException("æ›´æ–°å®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }

    /**
     * ç«‹å³æ‰§è¡Œä»»åŠ¡
     */
    public static void run(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
        	//å‚æ•°
        	JobDataMap dataMap = new JobDataMap();
        	dataMap.put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);
        	
            scheduler.triggerJob(getJobKey(scheduleJob.getJobId()), dataMap);
        } catch (SchedulerException e) {
            throw new RRException("ç«‹å³æ‰§è¡Œå®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }

    /**
     * æš‚åœä»»åŠ¡
     */
    public static void pauseJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.pauseJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("æš‚åœå®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }

    /**
     * æ¢å¤ä»»åŠ¡
     */
    public static void resumeJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.resumeJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("æš‚åœå®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }

    /**
     * åˆ é™¤å®šæ—¶ä»»åŠ¡
     */
    public static void deleteScheduleJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.deleteJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("åˆ é™¤å®šæ—¶ä»»åŠ¡å¤±è´¥", e);
        }
    }
}

package io.renren.modules.job.utils;

import io.renren.common.exception.RRException;
import io.renren.common.utils.Constant;
import io.renren.modules.job.entity.ScheduleJobEntity;
import org.quartz.*;

/**
 * Schedule utilities
 */
public class ScheduleUtils {
    private final static String JOB_NAME = "TASK_";
    
    /**
     * Get trigger key
     */
    public static TriggerKey getTriggerKey(Long jobId) {
        return TriggerKey.triggerKey(JOB_NAME + jobId);
    }
    
    /**
     * Get job key
     */
    public static JobKey getJobKey(Long jobId) {
        return JobKey.jobKey(JOB_NAME + jobId);
    }

    /**
     * Get cron trigger
     */
    public static CronTrigger getCronTrigger(Scheduler scheduler, Long jobId) {
        try {
            return (CronTrigger) scheduler.getTrigger(getTriggerKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("Get schedule job CronTrigger error", e);
        }
    }

    /**
     * Create schedule job
     */
    public static void createScheduleJob(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
        	// Create job detail
            JobDetail jobDetail = JobBuilder.newJob(ScheduleJob.class).withIdentity(getJobKey(scheduleJob.getJobId())).build();

            // Create cron schedule
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            // Create trigger with cron expression
            CronTrigger trigger = TriggerBuilder.newTrigger().withIdentity(getTriggerKey(scheduleJob.getJobId())).withSchedule(scheduleBuilder).build();

            // Put job parameters into job data map
            jobDetail.getJobDataMap().put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);

            scheduler.scheduleJob(jobDetail, trigger);
            
            // Pause job if status is pause
            if(scheduleJob.getStatus() == Constant.ScheduleStatus.PAUSE.getValue()){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }
        } catch (SchedulerException e) {
            throw new RRException("Create schedule job error", e);
        }
    }
    
    /**
     * Update schedule job
     */
    public static void updateScheduleJob(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
            TriggerKey triggerKey = getTriggerKey(scheduleJob.getJobId());

            // Create cron schedule
            CronScheduleBuilder scheduleBuilder = CronScheduleBuilder.cronSchedule(scheduleJob.getCronExpression())
            		.withMisfireHandlingInstructionDoNothing();

            CronTrigger trigger = getCronTrigger(scheduler, scheduleJob.getJobId());
            
            // Create new trigger with cron expression
            trigger = trigger.getTriggerBuilder().withIdentity(triggerKey).withSchedule(scheduleBuilder).build();
            
            // Update job data map
            trigger.getJobDataMap().put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);
            
            scheduler.rescheduleJob(triggerKey, trigger);
            
            // Pause job if status is pause
            if(scheduleJob.getStatus() == Constant.ScheduleStatus.PAUSE.getValue()){
            	pauseJob(scheduler, scheduleJob.getJobId());
            }
            
        } catch (SchedulerException e) {
            throw new RRException("Update schedule job error", e);
        }
    }

    /**
     * Run schedule job
     */
    public static void run(Scheduler scheduler, ScheduleJobEntity scheduleJob) {
        try {
        	// Job data map
        	JobDataMap dataMap = new JobDataMap();
        	dataMap.put(ScheduleJobEntity.JOB_PARAM_KEY, scheduleJob);
        	
            scheduler.triggerJob(getJobKey(scheduleJob.getJobId()), dataMap);
        } catch (SchedulerException e) {
            throw new RRException("Run schedule job error", e);
        }
    }

    /**
     * Pause schedule job
     */
    public static void pauseJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.pauseJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("Pause schedule job error", e);
        }
    }

    /**
     * Resume schedule job
     */
    public static void resumeJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.resumeJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("Resume schedule job error", e);
        }
    }

    /**
     * Delete schedule job
     */
    public static void deleteScheduleJob(Scheduler scheduler, Long jobId) {
        try {
            scheduler.deleteJob(getJobKey(jobId));
        } catch (SchedulerException e) {
            throw new RRException("Delete schedule job error", e);
        }
    }
}
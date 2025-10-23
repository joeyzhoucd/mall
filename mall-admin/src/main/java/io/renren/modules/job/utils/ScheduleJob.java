

package io.renren.modules.job.utils;

import io.renren.common.utils.SpringContextUtils;
import io.renren.modules.job.entity.ScheduleJobEntity;
import io.renren.modules.job.entity.ScheduleJobLogEntity;
import io.renren.modules.job.service.ScheduleJobLogService;
import org.apache.commons.lang.StringUtils;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.lang.reflect.Method;
import java.util.Date;



public class ScheduleJob extends QuartzJobBean {
	private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        ScheduleJobEntity scheduleJob = (ScheduleJobEntity) context.getMergedJobDataMap()
        		.get(ScheduleJobEntity.JOB_PARAM_KEY);
        
        //Å½Â·Ââ€“spring bean
        ScheduleJobLogService scheduleJobLogService = (ScheduleJobLogService) SpringContextUtils.getBean("scheduleJobLogService");
        
        //â€¢Â°ÂÂ®Âºâ€œÂ¿ÂÂ­Ëœâ€°Â¡Å’Â®Â°Â½â€¢
        ScheduleJobLogEntity log = new ScheduleJobLogEntity();
        log.setJobId(scheduleJob.getJobId());
        log.setBeanName(scheduleJob.getBeanName());
        log.setParams(scheduleJob.getParams());
        log.setCreateTime(new Date());
        
        //Â»Â»Å Â¡Â¼â‚¬â€¹â€”Â¶â€”Â´
        long startTime = System.currentTimeMillis();
        
        try {
            //â€°Â¡Å’Â»Â»Å Â¡
        	logger.debug("Â»Â»Å Â¡â€¡â€ Â¤â€¡â€°Â¡Å’Â¼Å’Â»Â»Å Â¡IDÂ¼Å¡" + scheduleJob.getJobId());

			Object target = SpringContextUtils.getBean(scheduleJob.getBeanName());
			Method method = target.getClass().getDeclaredMethod("run", String.class);
			method.invoke(target, scheduleJob.getParams());
			
			//Â»Â»Å Â¡â€°Â¡Å’â‚¬Â»â€”Â¶â€¢Â¿
			long times = System.currentTimeMillis() - startTime;
			log.setTimes((int)times);
			//Â»Â»Å Â¡Å Â¶â‚¬Â    0Â¼Å¡Ë†ÂÅ Å¸    1Â¼Å¡Â¤Â±Â´
			log.setStatus(0);
			
			logger.debug("Â»Â»Å Â¡â€°Â¡Å’Â®Å’Â¯â€¢Â¼Å’Â»Â»Å Â¡IDÂ¼Å¡" + scheduleJob.getJobId() + "  â‚¬Â»â€¦Â±â‚¬â€”â€”Â¶Â¼Å¡" + times + "Â¯Â«â€™");
		} catch (Exception e) {
			logger.error("Â»Â»Å Â¡â€°Â¡Å’Â¤Â±Â´Â¼Å’Â»Â»Å Â¡IDÂ¼Å¡" + scheduleJob.getJobId(), e);
			
			//Â»Â»Å Â¡â€°Â¡Å’â‚¬Â»â€”Â¶â€¢Â¿
			long times = System.currentTimeMillis() - startTime;
			log.setTimes((int)times);
			
			//Â»Â»Å Â¡Å Â¶â‚¬Â    0Â¼Å¡Ë†ÂÅ Å¸    1Â¼Å¡Â¤Â±Â´
			log.setStatus(1);
			log.setError(StringUtils.substring(e.toString(), 0, 2000));
		}finally {
			scheduleJobLogService.save(log);
		}
    }
}

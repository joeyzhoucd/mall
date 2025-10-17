/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

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


/**
 * å®šæ—¶ä»»åŠ¡
 *
 * @author Mark sunlightcs@gmail.com
 */
public class ScheduleJob extends QuartzJobBean {
	private Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        ScheduleJobEntity scheduleJob = (ScheduleJobEntity) context.getMergedJobDataMap()
        		.get(ScheduleJobEntity.JOB_PARAM_KEY);
        
        //èŽ·å–spring bean
        ScheduleJobLogService scheduleJobLogService = (ScheduleJobLogService) SpringContextUtils.getBean("scheduleJobLogService");
        
        //æ•°æ®åº“ä¿å­˜æ‰§è¡Œè®°å½•
        ScheduleJobLogEntity log = new ScheduleJobLogEntity();
        log.setJobId(scheduleJob.getJobId());
        log.setBeanName(scheduleJob.getBeanName());
        log.setParams(scheduleJob.getParams());
        log.setCreateTime(new Date());
        
        //ä»»åŠ¡å¼€å§‹æ—¶é—´
        long startTime = System.currentTimeMillis();
        
        try {
            //æ‰§è¡Œä»»åŠ¡
        	logger.debug("ä»»åŠ¡å‡†å¤‡æ‰§è¡Œï¼Œä»»åŠ¡IDï¼š" + scheduleJob.getJobId());

			Object target = SpringContextUtils.getBean(scheduleJob.getBeanName());
			Method method = target.getClass().getDeclaredMethod("run", String.class);
			method.invoke(target, scheduleJob.getParams());
			
			//ä»»åŠ¡æ‰§è¡Œæ€»æ—¶é•¿
			long times = System.currentTimeMillis() - startTime;
			log.setTimes((int)times);
			//ä»»åŠ¡çŠ¶æ€    0ï¼šæˆåŠŸ    1ï¼šå¤±è´¥
			log.setStatus(0);
			
			logger.debug("ä»»åŠ¡æ‰§è¡Œå®Œæ¯•ï¼Œä»»åŠ¡IDï¼š" + scheduleJob.getJobId() + "  æ€»å…±è€—æ—¶ï¼š" + times + "æ¯«ç§’");
		} catch (Exception e) {
			logger.error("ä»»åŠ¡æ‰§è¡Œå¤±è´¥ï¼Œä»»åŠ¡IDï¼š" + scheduleJob.getJobId(), e);
			
			//ä»»åŠ¡æ‰§è¡Œæ€»æ—¶é•¿
			long times = System.currentTimeMillis() - startTime;
			log.setTimes((int)times);
			
			//ä»»åŠ¡çŠ¶æ€    0ï¼šæˆåŠŸ    1ï¼šå¤±è´¥
			log.setStatus(1);
			log.setError(StringUtils.substring(e.toString(), 0, 2000));
		}finally {
			scheduleJobLogService.save(log);
		}
    }
}

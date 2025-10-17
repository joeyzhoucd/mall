/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.aspect;

import com.google.gson.Gson;
import io.renren.common.annotation.SysLog;
import io.renren.common.utils.HttpContextUtils;
import io.renren.common.utils.IPUtils;
import io.renren.modules.sys.entity.SysLogEntity;
import io.renren.modules.sys.entity.SysUserEntity;
import io.renren.modules.sys.service.SysLogService;
import org.apache.shiro.SecurityUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Date;


/**
 * ç³»ç»Ÿæ—¥å¿—ï¼Œåˆ‡é¢å¤„ç†ç±»
 *
 * @author Mark sunlightcs@gmail.com
 */
@Aspect
@Component
public class SysLogAspect {
	@Autowired
	private SysLogService sysLogService;
	
	@Pointcut("@annotation(io.renren.common.annotation.SysLog)")
	public void logPointCut() { 
		
	}

	@Around("logPointCut()")
	public Object around(ProceedingJoinPoint point) throws Throwable {
		long beginTime = System.currentTimeMillis();
		//æ‰§è¡Œæ–¹æ³•
		Object result = point.proceed();
		//æ‰§è¡Œæ—¶é•¿(æ¯«ç§’)
		long time = System.currentTimeMillis() - beginTime;

		//ä¿å­˜æ—¥å¿—
		saveSysLog(point, time);

		return result;
	}

	private void saveSysLog(ProceedingJoinPoint joinPoint, long time) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		Method method = signature.getMethod();

		SysLogEntity sysLog = new SysLogEntity();
		SysLog syslog = method.getAnnotation(SysLog.class);
		if(syslog != null){
			//æ³¨è§£ä¸Šçš„æè¿°
			sysLog.setOperation(syslog.value());
		}

		//è¯·æ±‚çš„æ–¹æ³•å
		String className = joinPoint.getTarget().getClass().getName();
		String methodName = signature.getName();
		sysLog.setMethod(className + "." + methodName + "()");

		//è¯·æ±‚çš„å‚æ•°
		Object[] args = joinPoint.getArgs();
		try{
			String params = new Gson().toJson(args);
			sysLog.setParams(params);
		}catch (Exception e){

		}

		//èŽ·å–request
		HttpServletRequest request = HttpContextUtils.getHttpServletRequest();
		//è®¾ç½®IPåœ°å€
		sysLog.setIp(IPUtils.getIpAddr(request));

		//ç”¨æˆ·å
		String username = ((SysUserEntity) SecurityUtils.getSubject().getPrincipal()).getUsername();
		sysLog.setUsername(username);

		sysLog.setTime(time);
		sysLog.setCreateDate(new Date());
		//ä¿å­˜ç³»ç»Ÿæ—¥å¿—
		sysLogService.save(sysLog);
	}
}

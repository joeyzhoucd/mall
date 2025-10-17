/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.aspect;

import io.renren.common.exception.RRException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Redisåˆ‡é¢å¤„ç†ç±»
 *
 * @author Mark sunlightcs@gmail.com
 */
@Aspect
@Configuration
public class RedisAspect {
    private Logger logger = LoggerFactory.getLogger(getClass());
    //æ˜¯å¦å¼€å¯redisç¼“å­˜  trueå¼€å¯   falseå…³é—­
    @Value("${spring.redis.open: false}")
    private boolean open;

    @Around("execution(* io.renren.common.utils.RedisUtils.*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Object result = null;
        if(open){
            try{
                result = point.proceed();
            }catch (Exception e){
                logger.error("redis error", e);
                throw new RRException("RedisæœåŠ¡å¼‚å¸¸");
            }
        }
        return result;
    }
}

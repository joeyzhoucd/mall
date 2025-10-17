/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package com.mall.common.utils;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;

/**
 * IPåœ°å€
 *
 * @author Mark sunlightcs@gmail.com
 */
public class IPUtils {
	private static Logger logger = LoggerFactory.getLogger(IPUtils.class);

	/**
	 * èŽ·å–IPåœ°å€
	 * 
	 * ä½¿ç”¨Nginxç­‰åå‘ä»£ç†è½¯ä»¶ï¼Œ åˆ™ä¸èƒ½é€šè¿‡request.getRemoteAddr()èŽ·å–IPåœ°å€
	 * å¦‚æžœä½¿ç”¨äº†å¤šçº§åå‘ä»£ç†çš„è¯ï¼ŒX-Forwarded-Forçš„å€¼å¹¶ä¸æ­¢ä¸€ä¸ªï¼Œè€Œæ˜¯ä¸€ä¸²IPåœ°å€ï¼ŒX-Forwarded-Forä¸­ç¬¬ä¸€ä¸ªéžunknownçš„æœ‰æ•ˆIPå­—ç¬¦ä¸²ï¼Œåˆ™ä¸ºçœŸå®žIPåœ°å€
	 */
	public static String getIpAddr(HttpServletRequest request) {
    	String ip = null;
        try {
            ip = request.getHeader("x-forwarded-for");
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (StringUtils.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
        } catch (Exception e) {
        	logger.error("IPUtils ERROR ", e);
        }
        
//        //ä½¿ç”¨ä»£ç†ï¼Œåˆ™èŽ·å–ç¬¬ä¸€ä¸ªIPåœ°å€
//        if(StringUtils.isEmpty(ip) && ip.length() > 15) {
//			if(ip.indexOf(",") > 0) {
//				ip = ip.substring(0, ip.indexOf(","));
//			}
//		}
        
        return ip;
    }
	
}

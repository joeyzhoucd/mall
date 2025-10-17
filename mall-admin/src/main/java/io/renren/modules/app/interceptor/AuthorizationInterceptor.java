/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.app.interceptor;


import io.jsonwebtoken.Claims;
import io.renren.common.exception.RRException;
import io.renren.modules.app.annotation.Login;
import io.renren.modules.app.utils.JwtUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * æƒé™(Token)éªŒè¯
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component
public class AuthorizationInterceptor extends HandlerInterceptorAdapter {
    @Autowired
    private JwtUtils jwtUtils;

    public static final String USER_KEY = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Login annotation;
        if(handler instanceof HandlerMethod) {
            annotation = ((HandlerMethod) handler).getMethodAnnotation(Login.class);
        }else{
            return true;
        }

        if(annotation == null){
            return true;
        }

        //èŽ·å–ç”¨æˆ·å‡­è¯
        String token = request.getHeader(jwtUtils.getHeader());
        if(StringUtils.isBlank(token)){
            token = request.getParameter(jwtUtils.getHeader());
        }

        //å‡­è¯ä¸ºç©º
        if(StringUtils.isBlank(token)){
            throw new RRException(jwtUtils.getHeader() + "ä¸èƒ½ä¸ºç©º", HttpStatus.UNAUTHORIZED.value());
        }

        Claims claims = jwtUtils.getClaimByToken(token);
        if(claims == null || jwtUtils.isTokenExpired(claims.getExpiration())){
            throw new RRException(jwtUtils.getHeader() + "å¤±æ•ˆï¼Œè¯·é‡æ–°ç™»å½•", HttpStatus.UNAUTHORIZED.value());
        }

        //è®¾ç½®userIdåˆ°requesté‡Œï¼ŒåŽç»­æ ¹æ®userIdï¼ŒèŽ·å–ç”¨æˆ·ä¿¡æ¯
        request.setAttribute(USER_KEY, Long.parseLong(claims.getSubject()));

        return true;
    }
}

package com.mall.cart.interceptor;

import com.mall.cart.constant.CartConstant;
import com.mall.cart.to.UserInfoTo;
import com.mall.session.vo.LoginUser;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Component
public class CartInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserInfoTo userInfoTo = new UserInfoTo();

        Object loginUser = request.getSession().getAttribute("loginUser");
        if (loginUser instanceof LoginUser) {
            LoginUser member = (LoginUser) loginUser;
            userInfoTo.setUserId(member.getId());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (CartConstant.TEMP_USER_COOKIE_NAME.equals(cookie.getName())) {
                    userInfoTo.setUserKey(cookie.getValue());
                    userInfoTo.setTempUser(true);
                }
            }
        }

        if (StringUtils.isBlank(userInfoTo.getUserKey())) {
            String uuid = UUID.randomUUID().toString().replace("-", "");
            userInfoTo.setUserKey(uuid);
            userInfoTo.setTempUser(false);
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, uuid);
            cookie.setDomain("mall.com");
            cookie.setMaxAge(CartConstant.TEMP_USER_COOKIE_TIMEOUT);
            response.addCookie(cookie);
        }

        threadLocal.set(userInfoTo);
        return true;
    }
}


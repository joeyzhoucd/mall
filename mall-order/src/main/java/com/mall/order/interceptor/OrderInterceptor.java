package com.mall.order.interceptor;

import com.mall.order.to.UserInfoTo;
import com.mall.session.vo.LoginUser;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OrderInterceptor implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) {
        UserInfoTo userInfoTo = new UserInfoTo();
        Object loginUser = request.getSession().getAttribute("loginUser");
        if (loginUser instanceof LoginUser) {
            LoginUser member = (LoginUser) loginUser;
            userInfoTo.setUserId(member.getId());
            userInfoTo.setUsername(member.getUsername());
        }
        threadLocal.set(userInfoTo);
        return true;
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        threadLocal.remove();
    }
}


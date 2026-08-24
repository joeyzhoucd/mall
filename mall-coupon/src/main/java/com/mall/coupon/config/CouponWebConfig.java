package com.mall.coupon.config;

import com.mall.coupon.interceptor.CouponInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CouponWebConfig implements WebMvcConfigurer {

    @Autowired
    @NonNull
    private CouponInterceptor couponInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(couponInterceptor).addPathPatterns("/**");
    }
}

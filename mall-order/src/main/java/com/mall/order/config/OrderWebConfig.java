package com.mall.order.config;

import com.mall.order.interceptor.OrderInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OrderWebConfig implements WebMvcConfigurer {

    @Autowired
    @NonNull
    private OrderInterceptor orderInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(orderInterceptor).addPathPatterns("/**");
    }
}


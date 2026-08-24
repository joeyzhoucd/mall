package com.mall.coupon.config;

import com.mall.coupon.interceptor.CouponInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
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

    /**
     * 秒杀抢购/提交地址这两个接口要等 RabbitMQ publisher confirm（最多 3 秒），
     * 用 Callable 做异步 servlet 处理，让 Tomcat 的请求线程在等待期间能立刻还回
     * 线程池服务别的请求——不然大库存量的秒杀一开抢，同一时刻的赢家数量本身就
     * 不小，每个都占一个线程等 3 秒，默认 200 线程的池子扛不住真实的爆发流量。
     * 单独开一个有界线程池给这部分异步处理用，不跟 Spring 默认的无界
     * SimpleAsyncTaskExecutor 混在一起（那个是来一个任务开一个线程，爆发流量下
     * 反而会把机器压垮）。
     */
    @Bean
    public ThreadPoolTaskExecutor seckillAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(200);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("seckill-async-");
        // 让登录态（CouponInterceptor 那个 ThreadLocal）跟着任务一起"跳"到线程池
        // 线程上，业务代码不用关心自己是不是在异步线程里跑，见 UserContextTaskDecorator
        // 的类注释。已经反编译确认过 Spring 5.3.25 对 execute()/submit(Callable) 都会
        // 套这层装饰，不是只对 execute() 生效。
        executor.setTaskDecorator(new UserContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(@NonNull AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(seckillAsyncExecutor());
        configurer.setDefaultTimeout(5000);
    }
}

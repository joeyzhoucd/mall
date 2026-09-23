package com.mall.cart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling 是给 CartEventLogger 的定时刷批用的。
// 【没有它 @Scheduled 会被静默忽略】——不报错、不警告，
// 表现就是埋点只进队列不出队，队列涨满后全部丢弃，而业务一切正常。
@EnableScheduling
@EnableFeignClients(basePackages = "com.mall.cart.feign")
@EnableDiscoveryClient
@SpringBootApplication
public class MallCartApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallCartApplication.class, args);
    }
}


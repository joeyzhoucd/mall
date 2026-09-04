package com.mall.ware;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.mall.ware.config.StockOutboxProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.mall.ware.feign")
@EnableScheduling
@MapperScan("com.mall.ware.dao")
@EnableConfigurationProperties(StockOutboxProperties.class)
public class MallWareApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallWareApplication.class, args);
    }

}

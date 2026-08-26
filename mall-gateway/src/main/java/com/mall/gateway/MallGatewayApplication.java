package com.mall.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// Boot 4 做了模块化拆分，各技术的自动配置类都挪到了自己的模块里，
// 包名从 org.springframework.boot.autoconfigure.<tech> 变成
// org.springframework.boot.<tech>.autoconfigure（这里是 spring-boot-jdbc 模块）。
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class MallGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallGatewayApplication.class, args);
    }

}

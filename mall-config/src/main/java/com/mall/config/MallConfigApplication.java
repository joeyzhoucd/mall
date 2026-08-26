package com.mall.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Spring Cloud Config Server，替代原来的 Nacos 配置中心。
 * <p>
 * 没有 @EnableDiscoveryClient——客户端是通过 K8s 的 Service DNS（http://mall-config:8888）
 * 直接找它的，不走服务发现。这样做是刻意的：配置中心是"启动阶段就要用"的东西，
 * 如果它自己也要先去注册中心查地址，就多了一层启动顺序依赖；而在 K8s 里 Service
 * 名本身就已经是一个稳定的固定入口了，服务发现在这里没有额外价值。
 */
@SpringBootApplication
@EnableConfigServer
public class MallConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallConfigApplication.class, args);
    }

}

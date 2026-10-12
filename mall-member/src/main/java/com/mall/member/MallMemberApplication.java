package com.mall.member;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

// 不再有 @EnableFeignClients：mall-member 唯一的 Feign 客户端 CouponFeignService 只服务于
// 脚手架演示接口 /member/member/coupons，2026-10-11 随它一起删除。以后要调别的服务再加回来。
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.mall.member.dao")
public class MallMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallMemberApplication.class, args);
    }

}

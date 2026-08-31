package com.mall.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 后台管理服务。
 * <p>
 * 阶段 7 的重建产物，替代原来的 renren-fast 脚手架。接口契约在
 * {@code src/main/resources/openapi/admin-api.yaml}，那是唯一依据——
 * 前端（mall-frontend 里的后台 UI）不重写，所以是后端适配前端的既有约定，
 * 而不是反过来。动手前先读那份契约头部的六条说明。
 */
// 让 AdminProperties 这个 record 形式的 @ConfigurationProperties 被扫描到。
// record 的构造器绑定 Boot 原生支持，不需要额外的 @EnableConfigurationProperties。
@ConfigurationPropertiesScan
// 后台文件管理要让 mall-thirdparty 删对象。存储凭据只放在 mall-thirdparty 一处，
// 不在这里再配一份 —— 同一份凭据出现在两个服务里，轮换时一定会漏掉一个，
// 而漏掉的那个不会立刻报错，会在下一次真正用到时才失败。
@EnableFeignClients(basePackages = "com.mall.admin.feign")
@EnableDiscoveryClient
@MapperScan("com.mall.admin.dao")
@SpringBootApplication
public class MallAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAdminApplication.class, args);
    }
}

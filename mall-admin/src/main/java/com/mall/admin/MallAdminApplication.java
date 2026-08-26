package com.mall.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

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
@EnableDiscoveryClient
@MapperScan("com.mall.admin.dao")
@SpringBootApplication
public class MallAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallAdminApplication.class, args);
    }
}

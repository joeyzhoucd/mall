package com.mall.testsupport;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 集成测试的统一入口注解：打 integration 标签 + 起 Spring 上下文 + 关掉一批
 * 在测试里没有意义或会拖慢启动的东西。
 *
 * <h3>为什么要关 Consul 和 Config Server</h3>
 * 这两个是「服务之间怎么找到彼此」的基础设施，跟被测服务自己的上下文能不能起来
 * 无关。不关的话每个测试都要多起两个容器，而且 Consul 的注册/健康检查是有重试和
 * 超时的 —— 连不上时不会立刻失败，而是把启动拖长十几秒再放弃。
 * 测试要么快要么明确失败，不要「慢慢地也能过」。
 *
 * <h3>为什么【不】关 mall.warmup.enabled</h3>
 * EagerConnectionWarmup 正是曾经一次性搞崩 9 个服务的那段代码
 * （{@code @Bean} 方法签名引用了可能不存在的类型，导致条件注解形同虚设）。
 * 它在真实的 DataSource / Redis / RabbitMQ 上跑一遍，才是这些集成测试
 * 最有价值的部分 —— 关掉它，测试就退化成「Spring 能不能拼出一个上下文」。
 *
 * <h3>为什么关 mall.warmup.request.enabled</h3>
 * RequestPathWarmup 会发 200 次自请求。默认的 MOCK web 环境里没有真实端口，
 * 它自己会跳过 HTTP 那部分，但仍会做 200 次 SELECT 1 —— 纯粹是浪费时间，
 * 而且它验证的是 JIT 预热，不是正确性。
 *
 * <h3>为什么关链路导出</h3>
 * 不关的话每个测试都会往 tempo:4318 发注定失败的请求并打整页堆栈，
 * 把真正的失败信息淹掉。
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Tag("integration")
@SpringBootTest(properties = {
        // 【必须显式关掉 fail-fast】2026-09-22 被 CI 教会的。
        // mall-search 为了不再静默降级，开了 spring.cloud.config.fail-fast=true。
        // 而 fail-fast 有个反直觉的行为：**它会压过 optional: 前缀** ——
        // 即使 import 写的是 optional:configserver:，拉不到照样抛
        // ConfigClientFailFastException 让上下文起不来。
        // 测试环境根本没有 Config Server，于是 mall-search 的集成测试全挂。
        //
        // 注意不能靠 "spring.config.import=" 清空来解决：空值会被当成「未设置」，
        // 各服务 application.yml 里的值照样生效（试过，CI 又挂了一次）。
        // 关掉 fail-fast 才是对症的 —— 它一关，optional: 就恢复成真正的「可选」。
        //
        // 【为什么本地跑不出这个问题】mvn package 带 -DskipITs，
        // 根 pom 又默认 excludedGroups=integration，两层都排除了它；
        // 而本机没有 Docker，也跑不了 Testcontainers。这类改动只能靠 CI 兜底。
        "spring.cloud.config.fail-fast=false",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.discovery.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.config.import-check.enabled=false",
        "mall.warmup.request.enabled=false",
        "management.tracing.export.enabled=false",
        "management.opentelemetry.tracing.export.otlp.enabled=false",
})
public @interface MallIntegrationTest {
}

# Mall Gateway 架构复习笔记

本文整理当前 `mall-gateway` 的请求流向、关键类、路由配置、服务发现、限流和熔断现状。

## 1. 整体分层

```text
客户端 / 浏览器
  ↓
mall-gateway Java 进程，监听 88
  ↓
Reactor Netty
  ↓
Spring WebFlux
  ↓
Spring Cloud Gateway
  ↓
Spring Cloud LoadBalancer
  ↓
Consul 服务发现
  ↓
具体微服务
```

各层职责：

| 层 | 职责 |
| --- | --- |
| Reactor Netty | 底层 HTTP/TCP Server，真正绑定 `server.port=88`，负责收发网络数据 |
| Spring WebFlux | Spring 响应式 Web 框架，负责请求包装、`DispatcherHandler` 分发、`HandlerMapping` 匹配 |
| Spring Cloud Gateway | 基于 WebFlux 的网关框架，负责 Route、Predicate、GatewayFilter、路径改写、限流、转发 |
| Spring Cloud LoadBalancer | 负责把 `lb://mall-admin` 这类服务名解析成具体实例 |
| Consul | 当前项目使用的服务注册与发现中心 |

当前项目的 Gateway 配置入口：

- `mall-gateway/src/main/resources/application.yml`
- `mall-gateway/src/main/java/com/mall/gateway/config/RateLimiterConfig.java`
- `mall-gateway/src/main/java/com/mall/gateway/config/MallCorsConfiguration.java`

## 2. 一个 HTTP 请求的调用链

```text
HTTP 请求
  ↓
Reactor Netty HttpServer
  ↓
ReactorHttpHandlerAdapter
  ↓
HttpWebHandlerAdapter
  ↓
WebFilter 链
  ↓
DispatcherHandler
  ↓
RoutePredicateHandlerMapping
  ↓
FilteringWebHandler
  ↓
GatewayFilterChain
  ↓
RouteToRequestUrlFilter
  ↓
ReactiveLoadBalancerClientFilter
  ↓
NettyRoutingFilter
  ↓
下游服务
```

关键类：

| 阶段 | 关键类 | 作用 |
| --- | --- | --- |
| Netty Server 启动 | `NettyReactiveWebServerFactory` | 根据 `server.port` 创建 Reactor Netty HTTP Server |
| Netty Server 运行 | `NettyWebServer` | 调用 `HttpServer.handle(...).bindNow()` 绑定端口 |
| Netty 到 Spring 适配 | `ReactorHttpHandlerAdapter` | 把 Reactor Netty request/response 适配到 Spring `HttpHandler` |
| WebFlux 入口 | `DispatcherHandler` | 遍历所有 `HandlerMapping`，找到能处理当前请求的 handler |
| Gateway 接入点 | `RoutePredicateHandlerMapping` | 匹配 Gateway Route，匹配成功后返回 `FilteringWebHandler` |
| Gateway 过滤器链 | `FilteringWebHandler` | 合并 `GlobalFilter` 和当前 route 的 `GatewayFilter` 并执行 |
| 写入目标 URL | `RouteToRequestUrlFilter` | 把 route 的 `uri` 写到 exchange attributes |
| 服务发现和负载均衡 | `ReactiveLoadBalancerClientFilter` | 处理 `lb://serviceId`，选择真实服务实例 |
| 真实代理转发 | `NettyRoutingFilter` | 用 Reactor Netty `HttpClient` 请求下游服务 |

## 3. Gateway 怎么接入 WebFlux

Gateway 不是 TCP Server，也不是独立中间件。它通过 Spring Boot 自动配置接入 WebFlux。

```text
spring-cloud-starter-gateway-server-webflux
  ↓
spring-cloud-gateway-server-webflux
  ↓
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
  ↓
GatewayAutoConfiguration
  ↓
注册 RoutePredicateHandlerMapping
注册 FilteringWebHandler
注册各种 RoutePredicateFactory / GatewayFilterFactory
```

核心类：

```java
org.springframework.cloud.gateway.config.GatewayAutoConfiguration
```

它会注册：

```text
GatewayProperties
PropertiesRouteDefinitionLocator
RouteDefinitionRouteLocator
RoutePredicateHandlerMapping
FilteringWebHandler
RewritePathGatewayFilterFactory
RequestRateLimiterGatewayFilterFactory
RouteToRequestUrlFilter
NettyRoutingFilter
```

WebFlux 的 `DispatcherHandler` 启动时会从 Spring 容器里收集所有 `HandlerMapping`，并按 order 排序：

```text
DispatcherHandler
  ↓
收集 HandlerMapping beans
  ↓
按 order 排序
  ↓
请求来了以后逐个调用 getHandler(exchange)
  ↓
谁先返回 handler，谁处理请求
```

Gateway 的接入点就是：

```java
org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping
```

它本质上是一个 WebFlux `HandlerMapping`。

## 4. 路由配置结构

当前项目路由配置路径：

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
```

一条 route 的基本结构：

```yaml
- id: product_route
  uri: lb://mall-product
  predicates:
    - Path=/api/product/**
  filters:
    - RewritePath=/api/(?<segment>.*),/$\{segment}
```

含义：

| 字段 | 作用 |
| --- | --- |
| `id` | 路由 ID，便于日志、监控、限流配置引用 |
| `uri` | 目标地址，`lb://mall-product` 表示通过服务发现找 `mall-product` |
| `predicates` | 匹配条件，比如 `Path`、`Host` |
| `filters` | 匹配后执行的过滤器，比如 `RewritePath`、`RequestRateLimiter` |

## 5. Predicate 和 Filter

`Predicate` 决定请求是否匹配某条 route。

项目例子：

```yaml
predicates:
  - Path=/api/product/**
```

```yaml
predicates:
  - Host=cart.mall.com
```

`Filter` 决定请求匹配 route 之后怎么处理。

项目例子：

```yaml
filters:
  - RewritePath=/api/(?<segment>.*),/$\{segment}
```

```yaml
filters:
  - name: RequestRateLimiter
```

配置中的名字会匹配 Spring 容器里的 factory：

| 配置名 | 对应类 |
| --- | --- |
| `Path` | `PathRoutePredicateFactory` |
| `Host` | `HostRoutePredicateFactory` |
| `RewritePath` | `RewritePathGatewayFilterFactory` |
| `RequestRateLimiter` | `RequestRateLimiterGatewayFilterFactory` |
| `CircuitBreaker` | `SpringCloudCircuitBreakerFilterFactory` |

## 6. 当前项目的路径路由

后台 API 主要通过 `/api/**` 前缀路由：

```text
/api/product/**      → mall-product
/api/member/**       → mall-member
/api/thirdparty/**   → mall-thirdparty
/api/ware/**         → mall-ware
/api/sys/**          → mall-admin
/api/captcha.jpg     → mall-admin
/api/coupon/**       → mall-coupon
```

示例：

```text
客户端请求：
/api/product/category/list

匹配路由：
Path=/api/product/**

RewritePath 后：
/product/category/list

转发目标：
lb://mall-product/product/category/list
```

`RewritePath=/api/(?<segment>.*),/$\{segment}` 的作用是去掉 `/api/` 前缀。

## 7. 当前项目的域名路由

前台页面类流量主要通过 Host 路由：

```text
auth.mall.com      → mall-auth
search.mall.com    → mall-search
cart.mall.com      → mall-cart
item.mall.com      → mall-product
seckill.mall.com   → mall-coupon
**.mall.com        → mall-product
```

路由顺序很重要。越具体的规则应该越靠前，宽泛兜底规则放后面。

例如：

```yaml
- id: order_route
  uri: lb://mall-order
  predicates:
    - Path=/order/**

- id: mall_cart_route
  uri: lb://mall-cart
  predicates:
    - Host=cart.mall.com
```

`/order/**` 必须放在 `Host=cart.mall.com` 前面，否则从购物车页进入结算时，请求可能被错误转给 `mall-cart`。

## 8. `lb://mall-admin` 怎么解析

`lb://mall-admin` 的含义：

```text
lb           = 使用 Spring Cloud LoadBalancer
mall-admin   = 服务发现里的 serviceId
```

当前项目使用 Consul：

```xml
<artifactId>spring-cloud-starter-consul-discovery</artifactId>
```

公共配置：

```properties
spring.cloud.consul.host=${CONSUL_HOST:localhost}
spring.cloud.consul.port=${CONSUL_PORT:8500}
spring.cloud.consul.discovery.prefer-ip-address=true
spring.cloud.consul.discovery.query-passing=true
```

解析链路：

```text
uri: lb://mall-admin
  ↓
RouteToRequestUrlFilter
  ↓
ReactiveLoadBalancerClientFilter
  ↓
LoadBalancerClientFactory
  ↓
DiscoveryClientServiceInstanceListSupplier
  ↓
ConsulReactiveDiscoveryClient
  ↓
Consul 查询 mall-admin 实例
  ↓
RoundRobinLoadBalancer 选一个实例
  ↓
NettyRoutingFilter 转发
```

Gateway 不关心底层是 Consul 还是 Nacos。它只使用 Spring Cloud 的服务发现抽象。如果 classpath 上换成 Nacos discovery starter，同样的 `lb://mall-admin` 可以通过 Nacos 查询。

生产环境不建议同时让一个 Gateway 隐式读取多个注册中心，否则可能出现同一个 `serviceId` 从不同注册中心拿到不同环境的实例。

## 9. 可观测性栈

> 本节是摘要。完整的接入方式、组件清单、查询语句和踩过的坑，见
> **`docs/observability-architecture.md`**。

Spring Boot 服务启动后，`Micrometer` 在应用进程里维护各种指标：HTTP 请求的**累计次数与累计耗时**、JVM 内存、线程、Hikari 连接池等。注意 **QPS 和错误率不是独立指标**——前者由查询时的 `rate()` 算出，后者靠 `status` / `outcome` / `exception` 等标签维度过滤得到，没有单独的「错误数」指标。

`Actuator` 提供把运行时信息暴露成 HTTP 端点的**机制**：`/actuator/health` 给 Consul 做服务健康检查（`health-check-path`，10 秒一次），`/actuator/health/readiness` 和 `/liveness` 两个**健康分组**给 K8s 探针，`/actuator/prometheus` 给 `Prometheus` 抓取。最后这个端点的**内容**由 `micrometer-registry-prometheus` 提供——没有这个依赖就没有这个端点，Actuator 本身不产生指标。

三条数据管道的**方向不同**，这是理解整套架构的关键：

- **指标是拉的**——Prometheus 通过 K8s 服务发现找到带 `prometheus.io/scrape=true` 注解的 pod 主动抓取。所以只有这一路需要服务发现。
- **日志是推的**——应用只管把日志写 stdout，`Alloy`（本项目用它；Promtail 已进入维护模式）在每个节点读容器日志文件推给 `Loki`。
- **链路是推的**——`Micrometer Tracing` 生成 trace/span，底层经 `OpenTelemetry` SDK/exporter 按 `OTLP` 协议推给 `Tempo`。

告警的**规则求值发生在 Prometheus**（规则文件在 git 里），条件成立才把告警推给 `Alertmanager`，由它做分组、去重、**抑制**、静默和通知。抑制和去重是两回事：去重是同一条告警来多次只发一次，抑制是**一条告警活跃时压掉另一条不同的告警**。Grafana 自己也有一套告警引擎，本项目**刻意不启用**。

`Grafana` 是统一查询层，自己不存任何业务数据，配了**四个**数据源：Prometheus、Loki、Tempo、**Alertmanager**（最后这个是「告警在 Grafana 里也能看」的原因）。日志与链路的互跳**不是自动的，是配出来的**：Alloy 把 trace_id 抽进 Loki 的结构化元数据（刻意不做成标签，标签基数一高索引会爆），Grafana 的 Loki 数据源用 `derivedFields` 生成跳转链接，反向靠 Tempo 数据源的 `tracesToLogsV2`。这依赖提取正则和实际日志格式对得上——对不上会静默失效，本项目曾因此让这个功能一直是死的。

另外链路是**采样**的（`TRACING_SAMPLE_RATE`，云上 0.1）：采样率不到 1.0 时，多数请求在 Tempo 里查不到，「通过 traceId 跳转」的前提是这条请求被采样到了。

注意：`Tempo` 不会发送到 Prometheus。Prometheus 存指标，Loki 存日志，Tempo 存链路，它们是并列的数据后端；Grafana 负责把它们查出来并关联展示。

`Datadog` 可以理解成把上面这一整套能力打包成一个一体化产品：Agent 负责采集，后端负责存指标、日志、链路和事件，控制台负责 Dashboard、查询、告警和关联跳转。区别是 Prometheus/Loki/Tempo/Alertmanager/Grafana 这套是多个开源组件拼起来，Datadog 是厂商把采集、存储、查询、告警、面板和关联分析都打通好的 SaaS/平台。

## 10. 限流架构

当前项目 Gateway 层已经配置限流，主要用于秒杀入口：

```yaml
- id: mall_seckill_route
  uri: lb://mall-coupon
  predicates:
    - Host=seckill.mall.com
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 50
        redis-rate-limiter.burstCapacity: 100
        redis-rate-limiter.requestedTokens: 1
        key-resolver: "#{@seckillGlobalKeyResolver}"
```

自定义 key resolver：

```java
@Bean
public KeyResolver seckillGlobalKeyResolver() {
    return exchange -> Mono.just("seckill-global");
}
```

执行流程：

```text
请求 Host=seckill.mall.com
  ↓
匹配 mall_seckill_route
  ↓
进入 FilteringWebHandler
  ↓
执行 RequestRateLimiterGatewayFilterFactory 生成的 filter
  ↓
KeyResolver.resolve(exchange)
  ↓
得到 key：seckill-global
  ↓
RedisRateLimiter.isAllowed(routeId, key)
  ↓
Redis Lua 令牌桶判断
  ↓
allowed=true：继续 chain.filter(exchange)
allowed=false：直接返回 429 Too Many Requests
```

关键类：

```java
org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory
org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter
org.springframework.cloud.gateway.filter.ratelimit.KeyResolver
```

为什么秒杀用全局 key：

```text
秒杀真正要保护的是 mall-coupon / Redis / DB 的总容量。
大量合法用户同时各发一两个请求，也可能压垮下游。
按用户或 IP 限流挡不住这种流量形态。
所以这里用 seckill-global，让所有秒杀请求共用一个令牌桶。
```

## 11. 熔断现状

> **2026-09-01 更新：项目已经有熔断了，但不在 Gateway 层。**
> 熔断挂在**所有 Feign 调用**上（`spring-cloud-starter-circuitbreaker-resilience4j`，
> 在 `mall-common` 里，配置见 `mall-common-default.properties` 的「熔断」段）。
> 本节说的「Gateway 层没有熔断」仍然准确 —— 那需要 `-reactor-` 版本，本项目刻意没引：
> `mall-gateway` 是 WebFlux，但它**不用 Feign**（全仓 `@FeignClient` 里没有它），
> 多引一个只会让它的 jar 白胖一圈。
>
> 两层的区别值得分清：
> - **Gateway 层熔断**：保护「网关到后端服务」这一跳，失败时可以回 `fallbackUri`。**目前没有。**
> - **Feign 调用熔断**：保护「服务到服务」这一跳，比如 `mall-order` 调 `mall-ware` 扣库存。**已有。**
>
> 详见 `docs/observability-architecture.md` §7 里的熔断指标，
> 以及 Grafana 的「韧性」面板（`/d/mall-resilience`）第四层。

当前项目 Gateway 层没有启用熔断。

仓库里没有看到：

```text
CircuitBreaker filter
fallbackUri
spring-cloud-starter-circuitbreaker-reactor-resilience4j
```

也就是说，当前 Gateway 做了：

```text
路由
路径改写
CORS
Redis RequestRateLimiter 限流
lb:// 服务发现和负载均衡
```

但没有做：

```text
Gateway 层 CircuitBreaker
Gateway 层 fallback
```

如果以后要加 Gateway 熔断，需要引入：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
</dependency>
```

路由配置示例：

```yaml
filters:
  - name: CircuitBreaker
    args:
      name: seckillCircuitBreaker
      fallbackUri: forward:/fallback/seckill
```

执行流程：

```text
CircuitBreaker GatewayFilter
  ↓
ReactiveCircuitBreakerFactory.create(name)
  ↓
ReactiveCircuitBreaker.run(chain.filter(exchange), fallback)
  ↓
下游正常：继续执行
下游异常/超时/断路器打开：走 fallbackUri
```

关键类：

```java
org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerFilterFactory
org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory
org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration
```

注意：`CircuitBreaker` 是 Gateway filter 名；真正熔断算法来自 Resilience4j。

## 12. CORS 跨域

当前项目在 Gateway 层统一配置 CORS：

```java
@Configuration
public class MallCorsConfiguration {
    @Bean
    public CorsWebFilter getCorsWebFilter() {
        UrlBasedCorsConfigurationSource configurationSource = new UrlBasedCorsConfigurationSource();

        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.addAllowedHeader("*");
        corsConfiguration.addAllowedMethod("*");
        corsConfiguration.addAllowedOriginPattern("*");
        corsConfiguration.setAllowCredentials(true);

        configurationSource.registerCorsConfiguration("/**", corsConfiguration);

        return new CorsWebFilter(configurationSource);
    }
}
```

作用：

```text
浏览器跨域请求在 Gateway 层统一处理。
不需要每个微服务重复配置 CORS。
```

生产环境建议把允许的 origin 收紧到明确域名，不要长期使用完全开放配置。

## 13. 复习总图

```text
客户端请求
  ↓
mall-gateway Java 进程 :88
  ↓
Reactor Netty HttpServer
  ↓
ReactorHttpHandlerAdapter
  ↓
HttpWebHandlerAdapter
  ↓
WebFlux WebFilter
  ↓
DispatcherHandler
  ↓
RoutePredicateHandlerMapping
      ├─ Path=/api/product/**
      ├─ Host=cart.mall.com
      └─ Host=seckill.mall.com
  ↓
FilteringWebHandler
  ↓
GatewayFilterChain
      ├─ RewritePath
      ├─ RequestRateLimiter
      ├─ CircuitBreaker（当前未启用）
      ├─ RouteToRequestUrlFilter
      ├─ ReactiveLoadBalancerClientFilter
      └─ NettyRoutingFilter
  ↓
Spring Cloud LoadBalancer
  ↓
ConsulReactiveDiscoveryClient
  ↓
下游微服务
```

一句话总结：

```text
mall-gateway 是一个 Spring Boot WebFlux 应用。
Reactor Netty 监听 88 端口。
WebFlux 的 DispatcherHandler 负责分发请求。
Gateway 通过 RoutePredicateHandlerMapping 接入 WebFlux。
匹配到 route 后进入 FilteringWebHandler。
GatewayFilterChain 完成路径改写、限流、负载均衡和转发。
服务名 lb://mall-xxx 通过 Consul + Spring Cloud LoadBalancer 解析。
当前有限流，没有 Gateway 层熔断。
```

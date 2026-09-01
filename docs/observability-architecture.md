# Mall 可观测性架构复习笔记

本文整理这套观测系统由哪些组件组成、应用侧怎么接入、平台侧怎么部署、以及怎么看。
配套的 Gateway 视角见 `docs/gateway-architecture.md`（那篇的 §9 是本文的摘要版）。

所有指标名、标签、端口、查询语句都是在集群上实测核对过的，不是照抄文档。

---

## 1. 组件清单

七个组件，各有明确边界。**存数据的三个是并列的，不互相转发。**

| 组件 | 角色 | 存数据 | 自己有界面 | 本项目端口 |
| --- | --- | --- | --- | --- |
| Micrometer | 应用进程内的指标门面与注册表 | 内存中的当前值 | 无 | — |
| Micrometer Tracing | 应用进程内生成 trace / span | 不存 | 无 | — |
| Actuator | 把运行时信息暴露成 HTTP 端点 | 不存 | 无 | 随服务端口 |
| **Prometheus** | 时序数据库 + 抓取器 + **告警规则求值** | 指标（留 3 天） | **有** | 9090 |
| **Loki** | 日志存储 | 日志（留 72h） | **无，只有 API** | 3100 |
| **Tempo** | 链路存储 | 链路（留 72h） | **无，只有 API** | 3200 |
| Alloy | 节点上的日志采集器（DaemonSet） | 不存 | **有**（看采集流水线） | 12345 |
| Alertmanager | 告警的分组 / 去重 / **抑制** / 静默 / 通知 | 几乎不存 | **有** | 9093 |
| **Grafana** | 统一查询与展示层 | **不存任何业务数据** | **有** | 3000 |

两个容易搞错的点：

- **Loki 和 Tempo 没有网页界面**（实测根路径 404、只回 `text/plain`），它们的数据只能通过
  Grafana 看。转发它们的端口是为了直接调 API。
- **告警规则求值发生在 Prometheus，不在 Grafana。** Grafana 自己也有一套告警引擎
  （Grafana-managed alerts），本项目**刻意不用**（数据源里 `handleGrafanaManagedAlerts: false`），
  Grafana 只负责把 Alertmanager 的结果显示出来。这是这套栈最常被误解的地方。

---

## 2. 三条数据管道，方向不一样

这个不对称是整套架构的枢纽，理解了它，很多设计就自然了。

```text
                     ┌── 指标：Prometheus 主动【拉】 ──┐
                     │   GET /actuator/prometheus      │
   业务服务 ─────────┤                                 ├──▶ Prometheus ──▶ Alertmanager
   (12 个)           │                                 │        │
                     ├── 日志：写 stdout，Alloy【推】 ─┼──▶ Loki │
                     │   读 /var/log/pods/*.log        │        │
                     │                                 │        │
                     └── 链路：应用自己【推】 ─────────┼──▶ Tempo│
                         OTLP/HTTP → tempo:4318        │        │
                                                       ▼        ▼
                                              Grafana（4 个数据源）
```

| 管道 | 方向 | 谁发起 | 后果 |
| --- | --- | --- | --- |
| 指标 | **拉（pull）** | Prometheus | 必须有**服务发现**才知道去抓谁 |
| 日志 | **推（push）** | Alloy（节点上） | 不需要服务发现，应用什么都不用做，只要写 stdout |
| 链路 | **推（push）** | 应用进程 | 不需要服务发现，但应用要配 exporter 地址 |

所以只有指标这一路需要服务发现——见 §3。

**本项目明确关掉了指标和日志的 OTLP 推送**，这两行在 `mall-common-default.properties`：

```properties
management.otlp.metrics.export.enabled=false
management.logging.export.otlp.enabled=false
```

原因：指标由 Prometheus 拉取（推一遍是重复且更贵），日志由 Alloy 读容器日志文件
（应用推日志会在应用崩溃时丢掉最后那批最关键的日志）。
只有链路是应用推的——因为链路数据必须由产生它的进程组装 span 上下文，没法从外部旁观。

> 踩过的坑：这几个属性的命名**不对称**。
> `management.<registry>.metrics.export.enabled` 是对的，
> 而日志那条反过来是 `management.logging.export.otlp.enabled`
> （`management.otlp.logging.export.enabled` 才是废弃的那个）。
> 写错不报错，只是静默失效——只能查配置元数据确认。
> 本项目有 `ConfigMetadataTest` 专门校验每个属性名都存在（见 §11）。

---

## 3. 应用侧怎么接入：指标

### 依赖

在 `mall-common/pom.xml`（所有服务继承）：

```xml
<!-- 暴露端点的机制 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<!-- 提供 /actuator/prometheus 这个端点的【内容】 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**关键区分**：`/actuator/prometheus` 不是 Actuator 自带的。Actuator 只提供「把端点暴露成 HTTP」
这个机制，端点的内容来自 `PrometheusMeterRegistry`。少了 `micrometer-registry-prometheus`
这个依赖，端点就不存在——「为什么没有 /actuator/prometheus」的答案通常是这个，不是 Actuator 配错了。

另外两个可选但很值的：

```xml
<!-- Feign 客户端的调用指标 + 跨服务传递 trace 上下文 -->
<dependency><groupId>io.github.openfeign</groupId><artifactId>feign-micrometer</artifactId></dependency>
<!-- JDBC 层的观测（连接获取耗时、查询耗时） -->
<dependency><groupId>net.ttddyy.observation</groupId><artifactId>datasource-micrometer</artifactId></dependency>
```

### 配置

```properties
# 只暴露需要的端点。不要图省事写 "*"，那会把 env/heapdump/threaddump 也开出去
management.endpoints.web.exposure.include=health,info,prometheus
# 生成 /actuator/health/readiness 和 /liveness 两个【健康分组】
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

### Prometheus 怎么发现要抓谁

用 Kubernetes 服务发现 + 注解，**不是把服务名写死在配置里**：

```yaml
# mall-deploy/charts/mall/templates/observability-prometheus.yaml
scrape_configs:
  - job_name: 'mall-services'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: [mall]
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: "true"
      # 把 app 标签变成 service，pod 名变成 pod —— Grafana 里按服务筛靠这两个
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: service
```

注解由 `backend-services.yaml` 的模板统一打，所以**新增一个服务不用改 Prometheus 配置**。

### 实际能拿到什么

集群实测 190 个指标名。常用的：

| 用途 | 指标 |
| --- | --- |
| HTTP（RED） | `http_server_requests_seconds_{count,sum,max}`，标签 `service/status/uri/method/outcome/exception` |
| Feign 客户端 | `http_client_requests_seconds_*` |
| 网关 | `spring_cloud_gateway_requests_seconds_count`，标签 `routeId/httpStatusCode/outcome` |
| JVM | `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes` / `jvm_threads_live_threads` |
| 连接池 | `hikaricp_connections_{active,idle,pending,max,timeout_total}` |
| Redis | `lettuce_seconds_*`（**不是** `lettuce_active_seconds_*`，后者是「当前在途」、静止恒为 0） |
| MQ | `rabbitmq_{published,consumed,failed_to_publish,unrouted_published}_total` |
| 日志 | `logback_events_total{level}`，level 取值 `debug error info trace warn` |
| 配置中心 | `spring_cloud_config_environment_find_seconds_*` |
| 熔断 | `resilience4j_circuitbreaker_*`（见 §7） |

**没有的东西，别去找**：

- **没有 QPS 指标**。Micrometer 给的是累计次数，QPS 是查询时 `rate()` 算出来的。
- **没有「错误数」指标**。错误是 `http_server_requests_seconds_count` 上的**标签维度**
  （`status` / `outcome` / `exception`），靠过滤得到。
- **没有 histogram bucket**，所以**算不了 p95**，只能用 `sum/count` 的平均值。
  要分位数得先开 `management.metrics.distribution.percentiles-histogram.*`。
- **没装 kube-state-metrics**，所以 pod 重启只能用 `process_uptime_seconds` 近似发现。

---

## 4. 应用侧怎么接入：日志

应用什么都不用做，**只要往 stdout 写**。剩下的是节点上的事。

### 日志格式（这里有个真实的坑）

`mall-common/src/main/resources/logback-common.xml`：

```xml
<property name="LOG_PATTERN"
    value="ts=%d{yyyy-MM-dd HH:mm:ss.SSS} level=%-5level traceId=%X{traceId:-} spanId=%X{spanId:-} thread=%thread logger=%logger{36} msg=%msg%n%ex" />
```

`traceId` / `spanId` 从 MDC 里取——**Micrometer Tracing 会自动往 MDC 里塞**，应用代码不用管。

> **坑（2026-09-01 修掉）**：`mall-common-default.properties` 里曾有一条
> `logging.pattern.level=%5p [应用名,traceId,spanId]`，看着像是日志格式的定义，**但它不生效**——
> 该属性只在 Spring Boot 自带的默认 console pattern 生效时才有用，而上面的
> `logback-common.xml` 自己定义了完整 pattern 和 root appender，压根不引用那个占位符。
>
> 代价是真实的：Alloy 抽取 trace_id 的正则是照着**那条失效配置**写的（方括号格式），
> 而真实日志是 `traceId=xxx` 的 key=value 格式，**于是从来没匹配上过**——
> Loki 里 0 条日志带 trace_id 元数据，「从日志跳到调用链」一直是死的，
> 而从采集到展示没有任何一处报错。
>
> 教训：改日志格式改 `logback-common.xml`，不是那条属性。而且这类「正则不匹配」
> 必须靠**对比行数**验证，不能靠「没报错」——见 §11。

### Alloy 怎么采

`mall-deploy/charts/mall/templates/observability-alloy.yaml`，四步：

```text
discovery.kubernetes     发现本节点上本命名空间的 pod
        ↓
discovery.relabel        提标签（namespace/pod/container/app），并拼出 __path__
        ↓
local.file_match         把 __path__ 里的通配符展开成具体文件
        ↓
loki.source.file         读文件
        ↓
loki.process             抽 trace_id / span_id 进【结构化元数据】
        ↓
loki.write               推给 Loki
```

三个必须知道的设计点：

1. **`discovery.kubernetes` 必须限定命名空间。** 它默认全集群 list/watch pod，而这个 chart 给
   Alloy 配的是命名空间级 Role（不是 ClusterRole），跨命名空间会被 RBAC 拒掉——
   而且失败方式极其安静：组件全部报 healthy、日志一条 error 都没有，就是采不到任何东西。

2. **`local.file_match` 这一步不能省。** `loki.source.file` 只接受绝对路径，
   直接把带 glob 的 target 喂给它会一条日志都采不到，也不报错。

3. **trace_id 放「结构化元数据」而不是标签。** Loki 是按标签组合建索引的，
   把 traceId 这种高基数值做成标签，索引会爆。结构化元数据不进索引但能过滤和展示。

### 标签

实测可用：`app` / `container` / `filename` / `namespace` / `pod` / `service_name`
+ Loki 自己识别的 `detected_level`（结构化元数据）。

> **坑**：Loki 的标签值查询**默认只看最近 1 小时**。空闲服务那段时间没有日志行，
> 看起来像「没采集到」——拉长时间范围就会出现。我因此误判过一次「Loki 只采到 2/12 个服务」，
> 实际 26 个 app 全都在。
>
> 另一个坑：`query_range` 的 `start`/`end` 传纯整数时按**纳秒**解析，传秒会被当成 1970 年，
> 等于查了全部历史，表现是「我查近 15 分钟却返回 18 小时前的日志」，而且不报错。

---

## 5. 应用侧怎么接入：链路

### 依赖

```xml
<!-- 一个 starter 搞定：它带 micrometer-tracing-bridge-otel + OTLP exporter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-opentelemetry</artifactId>
</dependency>
```

分工是这样的：**Micrometer Tracing** 是门面（负责在应用里划分 span、往 MDC 塞 traceId），
**OpenTelemetry SDK** 是它底下的实现，**OTLP exporter** 负责按协议发出去。
应用代码只面对 Micrometer 的抽象，换后端不用改代码。

### 配置

```properties
management.tracing.sampling.probability=${TRACING_SAMPLE_RATE:1.0}
# 注意属性名：management.otlp.tracing.endpoint 是 error 级废弃的，
# Boot 4 把 OTel 配置统一收到了 management.opentelemetry.* 下面
management.opentelemetry.tracing.export.otlp.endpoint=${OTLP_TRACING_ENDPOINT:http://tempo:4318/v1/traces}
```

> **坑（踩过两层）**：这个属性套了两层——先是少了
> `spring-boot-starter-opentelemetry` 导致自动配置压根没加载，就算加载了，
> 旧属性名一样绑不上。**两层都不报错。**

### 采样率：这是要认真对待的一个值

代码默认 1.0，`values.yaml` 里通过环境变量给成 0.1，本地 `values-local.yaml` 覆盖成 1.0。

| 值 | 后果 |
| --- | --- |
| 1.0（全采样） | 手工观察好用；但**实测上万请求的压测直接把 Tempo 打成 OOMKilled**（堆 640Mi） |
| 0.1 | 空闲时够诊断；但自己点一下，九成概率查不到那条链路 |

**全采样还有个不那么明显的副作用**：健康检查和指标抓取本身也被记成链路。
实测 200 条链路样本里 86% 是噪声——`/actuator/health` 占 68%、`/actuator/prometheus` 占 18%
（12 个服务 × Consul 每 10 秒 + K8s 双探针 + Prometheus 每 15 秒）。
过滤办法见 §9；要根治得在应用里加 `ObservationPredicate` 把 `/actuator` 排除出观测。

更彻底的方案是**尾部采样**（全收下来、只保留出错和慢的），但那要额外部署一个
OpenTelemetry Collector 做采样决策，这套集群暂时不上。

---

## 6. 平台侧：文件在哪

全部走 GitOps，**没有任何配置是在界面上点出来的**。

| 要改什么 | 文件 |
| --- | --- |
| 告警规则 | `mall-deploy/charts/mall/files/alert-rules.yml` |
| Alertmanager（分组/抑制/通知渠道） | `mall-deploy/charts/mall/templates/observability-alerting.yaml` |
| Grafana 面板 | `mall-deploy/charts/mall/files/dashboards/*.json` |
| Grafana 数据源 | `mall-deploy/charts/mall/templates/observability-grafana.yaml` |
| Prometheus 抓取配置 | `mall-deploy/charts/mall/templates/observability-prometheus.yaml` |
| 日志采集 | `mall-deploy/charts/mall/templates/observability-alloy.yaml` |
| Loki / Tempo | `mall-deploy/charts/mall/templates/observability-loki-tempo.yaml` |
| 镜像版本 / 资源 / 保留期 / 域名 | `mall-deploy/charts/mall/values.yaml`（本地覆盖在 `values-local.yaml`） |

流程：**改文件 → commit → push 到 Gitee → ArgoCD 同步 → pod 因 checksum 注解重启**。

### 两条不那么显然的工程约定

**1. 规则和面板放 `files/`，用 `.Files.Get` / `.Files.Glob` 读入，不放 `templates/`。**

因为它们内部大量使用 `{{ }}`——Prometheus 的 `{{ $labels.service }}`、
Grafana 图例的 `{{service}}`——和 Helm 的模板语法直接撞车，放 `templates/` 会被 Helm
抢先解析然后报错。`.Files.Get` 是原样读入。附带好处：这些文件保持可独立 lint
（`promtool check rules` 能直接跑），也能从 Grafana UI 导出导入来迭代。

**2. 每个组件都要 `checksum/config` 注解。**

Prometheus / Alertmanager / Alloy / Loki / Tempo / Grafana **都不会自动重载配置文件**：
ConfigMap 的内容在挂载卷里会更新，但进程只在启动时解析一次。
没有这个注解的话，改了 ConfigMap 而 pod 不重启，等于**改动静默不生效**。
实际踩过：Alloy 的修复推上去了、ConfigMap 也更新了，但 pod 是更新前启动的，跑的还是旧配置。

```yaml
annotations:
  checksum/config: {{ include "mall.prometheusConfig" . | sha256sum }}
  # 规则文件单独算一份 —— 只改规则不改主配置时，上面那个哈希不会变
  checksum/rules: {{ .Files.Get "files/alert-rules.yml" | sha256sum }}
```

---

## 7. 告警是怎么串起来的

```text
Prometheus 每 15s 求值规则 ──条件成立──▶ 推给 Alertmanager ──▶ 分组/抑制/静默 ──▶ 通知渠道
     （rule_files）                    （alerting.alertmanagers）              （receivers）
```

`prometheus.yml` 里的两段：

```yaml
rule_files:
  - /etc/prometheus/rules/*.yml     # 用通配符，以后拆多个文件不用改这里
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

### 当前 19 条规则，7 组

`可用性` / `流量与错误` / `韧性` / `资源` / `中间件` / `日志` / `心跳`。

### 三条刻意的设计

**1. 错误率规则排除 503。** 秒杀降级用 503 表示「暂时无法处理」，不是故障。
混进 5xx 会让降级噪声淹没真实故障：

```yaml
expr: |
  sum by (service) (rate(http_server_requests_seconds_count{status=~"5..",status!="503"}[5m]))
  / sum by (service) (rate(http_server_requests_seconds_count[5m])) > 0.05
```

**2. 有一条恒定触发的心跳规则。**

```yaml
- alert: 告警链路心跳
  expr: vector(1)
```

告警系统最糟的失效方式是**安静地不工作**——规则写错指标名、Alertmanager 连不上、路由配错，
全都表现为「没有告警」，而**「没有告警」和「一切正常」在界面上长得一模一样**。
这条恒定触发，所以只要它**不在** Alertmanager 里，就说明链路断了。
总览面板上有一格专门显示它。

**3. 抑制（inhibition）和去重是两回事。**

去重是同一条告警来多次只发一次；抑制是**一条告警活跃时压掉另一条不同的告警**：

```yaml
inhibit_rules:
  - source_matchers: [severity = "critical"]
    target_matchers: [severity = "warning"]
    equal: ['service']
```

实测验证过：熔断打开时，`熔断器已打开`(critical) 活跃，同一 service 的
`熔断正在拒绝请求`(warning) 在 Alertmanager 里状态是 `suppressed`。

### 熔断的指标（韧性组用到）

指标名是跑 `TaggedCircuitBreakerMetrics` 实测出来的：

| 指标 | 说明 |
| --- | --- |
| `resilience4j_circuitbreaker_state{name,state}` | **多值 Gauge**，当前状态那条为 1。state 取值 `closed/open/half_open/disabled/forced_open/metrics_only` |
| `resilience4j_circuitbreaker_calls_seconds_count{kind}` | TIMER，kind = `successful/failed/ignored` |
| `resilience4j_circuitbreaker_not_permitted_calls_total` | 电路打开时被直接拒绝的数量 |
| `resilience4j_circuitbreaker_failure_rate` / `_slow_call_rate` | **调用数不足 `minimum-number-of-calls` 时返回 -1** |

最后那条要特别注意：面板必须用 `>= 0` 过滤，否则图上会画出一条 -1 的线。
`mall-common` 里有 `CircuitBreakerConfigTest` 专门锁住这几个指标名——上游改名时是**测试先炸**，
而不是面板悄悄变空。

---

## 8. Grafana 怎么把它们关联起来

### 四个数据源

```yaml
# observability-grafana.yaml
- name: Prometheus   uid: prometheus     url: http://prometheus:9090
- name: Loki         uid: loki           url: http://loki:3100
- name: Tempo        uid: tempo          url: http://tempo:3200
- name: Alertmanager uid: alertmanager   url: http://alertmanager:9093
```

**`uid` 必须显式写死。** 不写的话 Grafana 会自动生成随机 uid，而下面的跳转配置是按
`datasourceUid: prometheus` / `loki` 引用的，对不上就**静默失效**：链接还在，点了跳不过去，不报错。
面板 JSON 里也是按这几个 uid 绑数据源的。

### 日志 ↔ 链路互跳：不是自动的，是配出来的

**日志 → 链路**（Loki 数据源的 `derivedFields`）：

```yaml
jsonData:
  derivedFields:
    - name: TraceID
      matcherType: label
      matcherRegex: trace_id      # 对应 Alloy 抽进结构化元数据的那个 key
      url: "$${__value.raw}"
      datasourceUid: tempo
```

**链路 → 日志**（Tempo 数据源的 `tracesToLogsV2`）+ **链路 → 指标**（`tracesToMetrics`）。

这条链路依赖三件事同时成立：日志格式里有 traceId、Alloy 的正则能匹配上、
Grafana 的 `matcherRegex` 和 Alloy 抽出来的 key 一致。**任何一环断了都不报错**——
这正是 §4 那个坑能潜伏那么久的原因。

### 面板走 provisioning，不在 UI 里手建

```yaml
providers:
  - name: mall
    folder: Mall
    allowUiUpdates: false        # 刻意的：防止 UI 改动和文件版本产生分叉
    options:
      path: /etc/grafana/dashboards
```

手建的面板存在 Grafana 自己的 PVC 里，PVC 一丢就没了，而且不在 git——
等于最关键的运维资产只存在于某个人的浏览器操作历史里。

> 挂载路径刻意**不放** `/var/lib/grafana` 下面：那已经是 PVC 的挂载点，
> 再把 ConfigMap 挂进它的子目录属于嵌套挂载，能用但行为依赖挂载顺序，出问题很难查。

---

## 9. 怎么看

### 入口

两条路都可以，**推荐 ingress**（一次性配置，重启和 pod 重建都不受影响）：

| 地址 | 组件 | 账号 |
| --- | --- | --- |
| http://grafana.mall.local | Grafana | `admin` / `admin` |
| http://alertmanager.mall.local | Alertmanager | 无认证 |
| http://prometheus.mall.local | Prometheus | 无认证 |

需要往 Windows hosts 里加行指向 Ingress 的 MetalLB IP（管理员操作，一次性）。

不方便改 hosts、或者要看 Alloy（它是 DaemonSet 没有 Service、没配 ingress）时，用端口转发：

```text
mall-deploy/tools/port-forward-observability.ps1          启动全部 6 个
mall-deploy/tools/port-forward-observability.ps1 -Status  查状态
mall-deploy/tools/port-forward-observability.ps1 -Stop    全部关闭
```

转发到 `127.0.0.1` 的 3000 / 9090 / 9093 / 12345 / 3100 / 3200。
**注意 `kubectl port-forward` 在对应 pod 重建时会断**（滚动更新、ArgoCD 同步都会），重跑即可。

### 四个面板

| 面板 | uid | 看什么 |
| --- | --- | --- |
| 总览 | `mall-overview` | 服务在线数、告警明细、**告警链路自检**、RED、堆内存、连接池 |
| 韧性 | `mall-resilience` | 四层闸门：限流(429) → 隔离舱 → 降级(503) → 熔断 |
| 配置中心 | `mall-config-server` | 谁在拉配置、哪个 profile、走哪个 repository、耗时、错误 |
| 日志 | `mall-logs` | 分级计数曲线 + Loki 原文 |

直达 `http://grafana.mall.local/d/<uid>`。

### 只有 Prometheus 自己界面才有的两个视图

- `/targets`——每个抓取目标的健康状况。「某个服务的指标怎么没了」要来这里查。
- `/alerts`——规则求值的细节（pending/firing 的具体时间点、`lastError`）。

### 只有 Alertmanager 界面才能做的一件事

**静默（silence）**。发布期间临时屏蔽某条告警，Grafana 那边是只读。
静默是运行时状态，**刻意不进 git**——它不该变成一次代码提交。

### 查询语句（都实测过）

**LogQL（Explore → Loki）**：

```text
{namespace="mall"}                                       全部
{namespace="mall", app="mall-coupon"}                    指定服务
{namespace="mall"} | detected_level =~ "error|warn"      只看 error/warn
{namespace="mall"} | trace_id != ""                      只看能跳转到链路的
{namespace="mall"} |= "HikariPool"                       文本搜索
```

用 `detected_level` 而不是对正文做 `level=` 正则，因为**各服务日志格式并不统一**：
业务服务是 mall-common 的 key=value 格式，而 `mall-config` 故意不依赖 mall-common
（否则它会向自己拉配置形成循环），用的是 Spring 默认格式。按正文匹配会整个漏掉它。

**TraceQL（Explore → Tempo）**：

```text
{ rootName !~ "http get /actuator.*" }                                    排掉噪声
{ rootName =~ "http get /member.*" }                                      只看某业务路径
{ rootName !~ "http get /actuator.*" && traceDuration > 100ms }           排噪声 + 只看慢的
{ rootName !~ "http get /actuator.*" && status = error }                  只看出错的
```

> **TraceQL 的一个非显而易见的坑**：`{}` 匹配的是 **span**，一条链路只要有任意一个 span
> 匹配就整条返回。所以 `{ name !~ "http get /actuator.*" }` **排不掉** actuator 链路——
> 它里面还有别的 span（比如 `connection`）。要用 trace 级的内置字段 `rootName` / `traceDuration`。
> 实测确认过：用 `name` 过滤后返回的根 span 里仍有 actuator，换 `rootName` 就干净了。

---

## 10. 从零接入一个新服务的清单

假设新加一个 `mall-xxx`：

| 步骤 | 做什么 | 谁负责 |
| --- | --- | --- |
| 1 | 依赖 `mall-common` | 自动获得 actuator + prometheus registry + OTel + 日志格式 |
| 2 | 什么都不用配 | `mall-common-default.properties` 已经把端点、采样、OTLP 地址都配好了 |
| 3 | 日志写 stdout | Logback 已由 `logback-common.xml` 统一配置 |
| 4 | 加进 `values.yaml` 的 `services` 清单 | `backend-services.yaml` 会自动打 `prometheus.io/scrape` 注解 |
| 5 | 不用改 Prometheus 配置 | 注解驱动的服务发现自动发现它 |
| 6 | 不用改 Alloy 配置 | 它采本节点全部 pod 的日志 |
| 7 | 不用改 Grafana 面板 | 面板的 `service` 模板变量是 `label_values(up{job="mall-services"}, service)` 动态取的 |

**也就是说：接一个新服务，只需要在 `values.yaml` 的 services 清单里加两行。**
这是「注解驱动 + 动态模板变量」换来的——如果当初把服务名写死在 Prometheus 配置和面板里，
每加一个服务就要改三处。

---

## 11. 这套里踩过的坑（都实测过）

按「失效有多安静」排序，越靠前越难发现。

| 坑 | 表现 | 怎么发现 |
| --- | --- | --- |
| Alloy 正则和日志格式不匹配 | Loki 里 0 条带 trace_id，跳转链接压根不出现，**无任何报错** | 对比两个 LogQL 的返回行数，必须**相等**（见表格下方） |
| 告警规则引用了不存在的指标 | 规则永远不触发，比没有告警更危险——它让你以为有人在看着 | 去掉阈值跑一遍确认底层序列有数据；加恒定触发的心跳规则 |
| 数据源 uid 没写死 | 跳转链接还在，点了跳不过去 | 显式写 uid |
| ConfigMap 改了 pod 没重启 | 跑的还是旧配置 | 加 `checksum/config` 注解 |
| `discovery.kubernetes` 没限定命名空间 | RBAC 拒掉，组件全部 healthy、日志无 error，就是采不到 | 看 `loki_source_file_files_active_total` 是否为 0 |
| 属性名废弃或拼错 | 静默回落默认值 | `ConfigMetadataTest` 用配置元数据校验每个属性名 |
| Loki 标签查询默认只看 1 小时 | 空闲服务像「没采到」 | 显式传时间范围（**纳秒**） |
| `failure_rate` 返回 -1 | 图上多一条 -1 的线 | 面板用 `>= 0` 过滤 |
| Feign 熔断器懒创建 | 部署后 `resilience4j_*` 指标数为 **0**，像是配置没生效 | 触发一次跨服务调用，指标立刻出现 |

第一条那个「对比行数」具体是这样做的——两个查询在同一时间窗内的返回行数**必须相等**：

```text
{namespace="mall"} |~ "traceId=[0-9a-f]{8}"     日志正文里有 traceId 的行数
{namespace="mall"} | trace_id != ""             拿到了 trace_id 结构化元数据的行数
```

修复后实测 30 / 30（含交集也是 30），两个负控制均为 0。
修复前是 30 / **0** —— 而这个 0 不会以任何形式报错。

**共同的方法论：每个「扫描没发现问题」的结论，都要先证明扫描本身有效。**
具体做法就是负控制——编造一个不可能存在的值，确认查询返回 0；以及正控制——
心跳规则恒定触发，看不到它才是出问题了。

---

## 12. 上云前必须重新评估的

当前是本地单人集群，为方便做了一些取舍。这些**不是疏漏，是有意的**，但上云前要逐条处理：

| 项 | 现状 | 上云要改成 |
| --- | --- | --- |
| Grafana 密码 | `values.yaml` 里明文 `admin` | 从 Sealed Secret 取 |
| Prometheus / Alertmanager / Alloy | **无任何认证**，且已暴露到 ingress | ingress 加 basic-auth / OAuth2 Proxy，或干脆不对外暴露 |
| `--web.enable-lifecycle` | 开着，意味着能 `POST /-/quit` 把 Prometheus 关掉 | 去掉这个 flag（本 chart 靠 checksum 重启加载配置，不依赖 `/-/reload`） |
| 保留期 | 指标 3 天、日志和链路 72 小时 | 按合规要求调大，并考虑对象存储后端 |
| 采样率 | 本地 1.0 | 回到 0.1 或上尾部采样 |
| 单副本 | Prometheus / Loki / Tempo / Grafana 都是 1 副本 + RWO PVC | 至少 Prometheus 要考虑远程写 + 高可用 |
| Alertmanager 存储 | `emptyDir`，重启丢静默记录 | 换 PVC |
| 通知渠道 | 没配，界面本身就是接收端 | 往 `receivers` 里加 webhook / 邮件 |

---

## 13. 复习总图

```text
【应用进程内】
  Micrometer ──────── 指标（内存中的注册表）
  Micrometer Tracing ─ span（往 MDC 塞 traceId/spanId）
       └── OpenTelemetry SDK ── OTLP exporter
  Logback ─────────── 按 LOG_PATTERN 写 stdout（含 traceId/spanId）
  Actuator ────────── /actuator/health、/health/readiness、/health/liveness、/actuator/prometheus

【平台】
  Prometheus ── K8s SD + prometheus.io/scrape 注解 ── 拉 /actuator/prometheus
       ├── rule_files 求值 ──▶ Alertmanager ── 分组/去重/抑制/静默 ──▶ （通知渠道）
       └── 存 3 天
  Alloy(DaemonSet) ── 读 /var/log/pods/*.log ── 抽 trace_id 进结构化元数据 ──▶ Loki（72h）
  各服务 ── OTLP/HTTP ──────────────────────────────────────────────▶ Tempo（72h）

【展示】
  Grafana ── 4 个数据源（Prometheus / Loki / Tempo / Alertmanager）
       ├── 面板走 provisioning，文件在 git 里，allowUiUpdates: false
       ├── 日志 → 链路：Loki 数据源的 derivedFields
       └── 链路 → 日志/指标：Tempo 数据源的 tracesToLogsV2 / tracesToMetrics
```

一句话概括：**Prometheus 存指标，Loki 存日志，Tempo 存链路，三者是并列的数据后端；
Grafana 负责查询与关联展示；Alertmanager 只管告警的投递治理。**

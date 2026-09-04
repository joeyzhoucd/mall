# Mall 成熟分布式电商能力路线图

更新日期：2026-09-04

## 状态图例

| 状态 | 含义 |
| --- | --- |
| <span style="color:#16833a;font-weight:700">已实现</span> | 代码里已经有相对清晰的闭环，后续主要是维护和验证。 |
| <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 有模块、实体、配置或局部链路，但距离成熟生产级还缺边界、治理、自动化或完整闭环。 |
| <span style="color:#c62828;font-weight:700">待实现</span> | 当前仓库里没有看到有效实现，后续需要从设计开始补。 |

## 当前判断

这个项目现在更像是“微服务电商基础架构 + 部分核心业务链路”。已经具备商品、会员、购物车、订单、库存、优惠/秒杀、搜索、后台、网关、认证、MQ、缓存、对象存储、可观测性、CI 构建等基础能力；距离成熟电商，主要差在交易一致性、真实支付闭环、库存闭环、风控、安全治理、数据分析、发布运维体系。

## 最值得补的优先级

| 优先级 | 能力 | 当前状态 | 备注 |
| --- | --- | --- | --- |
| 1 | 支付 + 支付回调幂等 + 对账 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 `PayMockController`、独立 `mall-payment` 本地模拟服务、订单侧支付网关客户端、支付/退款表实体、签名校验、独立回调事件表、回调幂等保护、主动查单补偿任务和对账文件处理；接口见 `docs/payment-mock.md`。还缺真实通道适配。 |
| 2 | 订单状态机 + 超时关单 + 库存解锁 | <span style="color:#16833a;font-weight:700">已实现</span> | 已有完整订单状态枚举、显式状态流转表、非法流转 CAS 保护、订单关闭监听、支付成功扣库存、关单释放库存，以及发货/收货完成/售后状态推进入口。 |
| 3 | 事务消息 / Outbox / 本地消息表 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 秒杀链路已有 `SeckillLocalMessage` 本地消息表和 confirm 等待；普通订单/支付状态流转已走 `oms_order_outbox_message`，库存失败通知已走 `wms_stock_outbox_message`，订单/库存 MQ 消费已补本地幂等记录；还缺跨服务统一消息治理后台。 |
| 4 | 死信队列 + 消费幂等 + 补偿任务 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有订单延迟队列、库存失败队列、消费失败 DLX/DLQ、DLQ 查看/重放/丢弃入口、秒杀对账任务、库存重试和订单/库存消费幂等；还缺统一告警、权限化人工处理后台和更细的重试策略。 |
| 5 | 多级缓存 + 热点保护 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 Redis 缓存、秒杀本地售罄标记、Redis Lua；还缺 Caffeine + Redis 多级缓存、缓存预热、热点 key 监控和标准化防穿透/击穿/雪崩策略。 |
| 6 | 网关统一鉴权 + 风控限流 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 Gateway 管理端 JWT 鉴权、入口限流；还缺前台统一认证、黑白名单、设备/IP/用户维度风控限流。 |
| 7 | 数据库迁移工具 Flyway/Liquibase | <span style="color:#c62828;font-weight:700">待实现</span> | 当前没有看到 Flyway/Liquibase 迁移目录和依赖。 |
| 8 | SLO 告警 + Runbook | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 Micrometer、Prometheus、Loki、Tempo、Grafana、Alertmanager 文档和业务指标；还缺正式 SLO、告警分级、值班流程、Runbook。 |
| 9 | 灰度发布 / 回滚 | <span style="color:#c62828;font-weight:700">待实现</span> | 当前 CI 能构建镜像，但没有看到按用户/地区/比例灰度和自动回滚闭环。 |
| 10 | 分库分表或读写分离 | <span style="color:#c62828;font-weight:700">待实现</span> | 当前没有看到 ShardingSphere、读写分离路由或订单分片策略。 |

## 业务中台 / 核心模块

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 支付系统：支付宝、微信、银行卡、退款、对账、支付回调幂等 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 `mall-payment` 本地模拟支付宝/微信/信用卡、订单侧支付网关客户端、支付单落库、签名校验、支付/退款实体、独立回调事件表、回调幂等保护、主动查单补偿任务和对账文件处理；下一步补真实通道适配。 |
| 订单状态机：订单创建、待支付、已支付、待发货、已发货、完成、取消、退款等状态流转 | <span style="color:#16833a;font-weight:700">已实现</span> | 已覆盖 `NEW/PAYED/SENT/RECEIVED/CLOSED/SERVICING/SERVICED`，通过显式流转表和服务层 CAS 更新保护非法流转，并记录订单操作历史。 |
| 库存中心：可售库存、锁定库存、扣减库存、库存回滚、库存流水 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有锁定、释放、扣减、CAS、失败重试；下一步补库存流水、库存对账、手工补偿和多仓规则。 |
| 促销中心：优惠券、满减、秒杀、拼团、会员价、活动互斥规则 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有优惠券、满减、会员价、秒杀表和秒杀链路；下一步补统一促销计算、互斥规则、叠加规则和拼团。 |
| 会员体系：等级、积分、成长值、权益、黑名单 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有会员、等级、积分/成长值历史等基础表；下一步补权益、黑名单、会员价联动和积分账本。 |
| 物流系统：发货、运单、物流轨迹、签收、售后退货 | <span style="color:#c62828;font-weight:700">待实现</span> | 当前库存/采购不等于物流履约；需要新增发货单、运单、轨迹、签收和承运商接口。 |
| 售后系统：退款、退货、换货、客服审核 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有订单退货申请、退货原因、退款信息实体和基础接口；下一步补审核流、退款流转、退货入库和换货。 |
| 结算/财务系统：商家结算、平台佣金、账单、发票 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要独立财务/结算模型、账期、佣金、发票、资金流水和审计。 |
| 运营后台：商品审核、活动配置、风控审核、数据看板 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 `mall-admin`、系统用户/角色/菜单、定时任务、商品/优惠基础管理；下一步补审核流、风控审核、运营看板。 |
| CMS/内容管理：首页轮播、专题页、推荐位、广告位 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有首页广告、专题相关表；下一步补推荐位、广告投放规则、上下线审批和缓存。 |

## 分布式一致性

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 分布式事务方案：Seata、事务消息、TCC、Saga、Outbox Pattern | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 秒杀局部使用本地消息表 + MQ confirm；普通订单、支付状态流转和库存失败通知已接入本地 Outbox；下一步补消费幂等和运营补偿闭环。 |
| 消息最终一致性：本地消息表、MQ confirm、消费幂等、失败重试、死信队列 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有秒杀本地消息表、订单 Outbox、库存 Outbox、confirm、订单延迟队列、库存失败队列、消费失败 DLQ 和订单/库存消费幂等记录；下一步补统一 DLQ 告警和权限化人工处理后台。 |
| 幂等体系：支付回调、下单、扣库存、发券、消息消费都需要 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有订单 token、防重复抢购、状态 CAS、订单号幂等、支付回调幂等和订单/库存 MQ 消费幂等；发券和更多跨服务命令幂等仍需补齐。 |
| 分布式锁治理：锁超时、锁续期、锁粒度、锁失败降级 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 秒杀对账使用 Redisson 锁和 watchdog；还缺全局锁规范、降级策略、指标和锁粒度治理。 |
| 补偿任务：超时关单、库存解锁、支付状态主动查询、消息重投 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有订单超时关闭、库存释放/重试、秒杀对账重投和支付主动查单；还缺通用补偿任务平台和人工干预入口。 |

## 高并发与性能

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 热点数据保护：本地缓存 Caffeine + Redis 多级缓存 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 秒杀已有本地售罄标记 + Redis；商品侧有 Redis 缓存配置；下一步引入 Caffeine、多级缓存封装和热点 key 治理。 |
| 缓存治理：缓存穿透、击穿、雪崩、预热、失效策略 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 有 Redis 使用和部分预热/本地标记；还缺统一缓存工具、空值缓存、互斥重建、随机 TTL、预热任务。 |
| 秒杀专用链路：资格校验、令牌、库存预热、异步下单、削峰 | <span style="color:#16833a;font-weight:700">已实现</span> | 已有 Redis Lua 扣名额、用户去重、本地消息表、MQ 异步建单、对账补偿、本地售罄保护。 |
| 读写分离：MySQL 主从、只读库、分库分表 | <span style="color:#c62828;font-weight:700">待实现</span> | 没有看到读写路由或主从数据源配置。 |
| 分库分表：ShardingSphere、订单按用户/订单号分片 | <span style="color:#c62828;font-weight:700">待实现</span> | 没有看到 ShardingSphere 或分片键设计。 |
| CDN：静态资源、商品图片、活动页缓存 | <span style="color:#c62828;font-weight:700">待实现</span> | 当前没有看到 CDN 接入配置。 |
| 对象存储图片处理：缩略图、WebP/AVIF、鉴黄、压缩 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 S3 兼容对象存储上传；图片处理、转码、审核、压缩还缺。 |

## 稳定性治理

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 服务限流：Gateway 入口限流，服务内部限流 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | Gateway 有限流配置，秒杀有隔离舱；普通服务内部限流还缺统一方案。 |
| 熔断降级策略：Resilience4j 与业务降级方案 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 公共配置已有 Resilience4j 参数；还缺按业务定义的 fallback、降级开关和演练。 |
| 超时治理：Feign、DB、Redis、MQ 全链路统一超时预算 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有部分 Feign/配置可观测；还缺统一超时预算表和强制校验。 |
| 隔离舱：线程池/连接池/信号量隔离 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 秒杀有 bulkhead；其他业务链路还缺标准化隔离。 |
| 灰度发布：按用户、地区、流量比例发布 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要网关/服务网格/发布平台支持。 |
| 服务降级开关：动态配置开关、活动开关、只读模式 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 有配置中心、秒杀活动开关雏形；还缺统一动态开关、只读模式和审计。 |
| 容量压测体系：压测脚本、容量基线、性能回归 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 文档和注释里有压测实测背景；还缺仓库化压测脚本、容量基线和 CI 性能回归。 |

## 安全与风控

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 统一认证中心：OAuth2/OIDC、SSO、Refresh Token | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有后台 JWT、前台登录/社交登录雏形；还不是统一 OAuth2/OIDC/SSO，缺 Refresh Token 和撤销机制。 |
| 权限模型：RBAC、菜单权限、按钮权限、数据权限 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 后台有用户、角色、菜单；按钮权限、数据权限和统一鉴权注解仍需补。 |
| 风控系统：刷单、薅羊毛、恶意注册、异常支付、设备指纹 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要规则、模型、黑名单、设备指纹和审核后台。 |
| 验证码/人机验证：图形验证码、短信验证码、滑块验证 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 后台登录有图形验证码；短信、滑块、注册/下单风控验证码还缺。 |
| 接口签名：开放 API、支付回调、防篡改 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | mock 支付回调有 HMAC 签名校验；还缺开放 API 签名规范和真实支付验签。 |
| 敏感数据治理：脱敏、加密、密钥管理、审计日志 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 JWT 密钥校验、配置指标敏感字段过滤、系统日志；还缺 PII 脱敏、字段加密、KMS 和审计闭环。 |
| WAF/防爬：限频、黑名单、UA/IP/设备策略 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要网关/WAF/风控服务配合实现。 |

## 数据与搜索推荐

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 数据仓库/湖仓：离线分析、经营报表 | <span style="color:#c62828;font-weight:700">待实现</span> | 当前没有离线数仓链路。 |
| 实时数仓：Kafka/Flink/ClickHouse/Doris | <span style="color:#c62828;font-weight:700">待实现</span> | 当前没有实时数仓组件。 |
| 推荐系统：猜你喜欢、相似商品、个性化排序 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要召回、排序、特征和实验平台。 |
| 搜索增强：同义词、纠错、热词、搜索埋点、排序学习 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 Elasticsearch 搜索服务；还缺同义词、纠错、热词、埋点和排序学习。 |
| 埋点系统：曝光、点击、加购、下单、支付转化漏斗 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有部分业务指标；还缺用户行为埋点和转化漏斗。 |
| AB 实验平台：活动页、推荐策略、搜索排序实验 | <span style="color:#c62828;font-weight:700">待实现</span> | 需要实验分流、指标归因和配置后台。 |

## 运维与平台化

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| 配置灰度/动态刷新：配置审计、灰度、生效记录 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 有 `mall-config` 和公共配置；还缺配置灰度、审计、生效记录和回滚。 |
| 注册中心高可用：Consul 集群、备份、健康检查治理 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 公共配置已切到 Consul 并治理健康检查；集群 HA、备份和故障演练仍需补。 |
| MQ 高可用：RabbitMQ 集群、Quorum Queue、死信监控 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 RabbitMQ 队列/交换机/绑定、confirm、消费失败 DLX/DLQ 和 DLQ 运维接口；还缺集群、Quorum Queue、告警和权限化处理后台。 |
| Redis 高可用：Sentinel/Cluster、持久化、备份、热 key 监控 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 配置里已有 Redis Sentinel；还缺 Cluster 方案、备份、持久化校验和热 key 监控。 |
| MySQL 高可用：主从、备份恢复、慢 SQL、审计 | <span style="color:#c62828;font-weight:700">待实现</span> | 没有看到主从、备份恢复、慢 SQL 治理和审计配置。 |
| CI/CD 完整流水线：构建、测试、镜像扫描、灰度、回滚 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | GitHub Actions 已有构建和镜像推送；还缺镜像扫描、灰度、回滚和环境审批。 |
| GitOps：ArgoCD、环境隔离和审批 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有部署仓库侧 ArgoCD 背景；当前仓库还需要把环境隔离和审批约束文档化。 |
| 可观测性告警闭环：SLO、告警分级、值班、Runbook | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有观测栈和架构文档；下一步补 SLO、告警分级、Runbook 和演练记录。 |

## 开发治理

| 能力 | 当前状态 | 现状与下一步 |
| --- | --- | --- |
| API 网关鉴权：服务侧鉴权 + 网关统一认证/限流/黑白名单 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 管理端 `/api/**` 已有网关 JWT 鉴权和入口限流；前台统一鉴权、黑白名单仍需补。 |
| 统一错误码：业务错误码、异常规范、国际化文案 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 `ErrorCode`、`R`、异常处理雏形；还缺全域错误码分段、异常规范和 i18n。 |
| 接口契约治理：OpenAPI、契约测试、版本兼容 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | `mall-admin` 已有手写 OpenAPI 和契约测试，公共 OpenAPI 自动配置已存在；其他服务和版本兼容策略还缺。 |
| 代码生成/脚手架：新服务模板、统一 starter | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 `mall-common`、`mall-mq-starter`、`mall-session-starter`；还缺标准新服务模板和生成器。 |
| 质量门禁：单测覆盖率、集成测试、静态扫描、依赖漏洞扫描 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有 JUnit 5、部分契约/配置/集成测试和 CI；还缺覆盖率阈值、静态扫描、依赖漏洞扫描。 |
| 数据库变更管理：Flyway / Liquibase | <span style="color:#c62828;font-weight:700">待实现</span> | 需要选择 Flyway 或 Liquibase，并为各服务建立版本化迁移目录。 |
| 日志审计：管理员操作日志、资金变更日志、库存变更日志 | <span style="color:#a66a00;font-weight:700">已实现，需加强</span> | 已有系统日志、操作历史、库存任务明细；资金审计、库存流水、管理员关键操作审计还需补齐。 |

## 推荐实施节奏

1. 先补交易闭环：支付单、支付回调幂等、订单状态机、超时关单、库存解锁/扣减一致性。
2. 再补消息闭环：在现有订单/库存 Outbox 基础上补消费幂等、DLQ 运营面和补偿任务后台。
3. 然后补可运营能力：DLQ 后台、补偿任务后台、库存/支付/订单对账页。
4. 最后补平台治理：Flyway、SLO + Runbook、灰度发布、读写分离或分库分表。

## 2026-09-04 消息闭环增量

- `mall-mq-starter` 显式声明消费失败 DLX/DLQ，并提供 `/mq/dlq` 队列概览、取样、重放和丢弃入口。
- `mall-order` 新增 `oms_mq_consume_message`，订单关单和秒杀建单监听器通过业务幂等键落本地消费记录。
- `mall-ware` 新增 `wms_stock_outbox_message` 和 `wms_mq_consume_message`，库存失败通知走 Outbox，库存释放/扣减/失败监听器接入消费幂等。
- 已通过 `mvn -pl mall-mq-starter,mall-order,mall-ware -am test` 验证。

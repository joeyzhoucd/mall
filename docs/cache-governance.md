# Mall 缓存治理

## 目标

缓存只解决热点读压力，不改变库存、订单、秒杀这些写路径的裁决权。裁决权仍然在数据库 CAS、Redis Lua 或消息幂等表里，缓存只能服务可回源的读模型。

## 接入边界

| 场景 | 做法 |
| --- | --- |
| 读多写少、可短暂过期 | 接 `MultiLevelCacheClient`，给独立 cache name 和明确 TTL |
| 不存在也会被频繁查询 | 开空值缓存，空值 TTL 必须短于正常 TTL |
| 写后立即影响前台展示 | 写成功后调用业务 invalidator，在事务提交后失效 |
| 下单、扣库存、支付状态推进 | 不从缓存判断成功失败，只能用原子 SQL、Redis Lua 或幂等表 |
| 高基数对象 | key 可以带业务 ID，指标标签不能带业务 ID |

## Cache Name

| 模块 | cache | key | 用途 | TTL |
| --- | --- | --- | --- | --- |
| `mall-product` | `product:sku-info` | `skuId` | SKU 基础信息与价格 | local 30s / Redis 10m |
| `mall-product` | `product:sku-item` | `skuId` | 商品详情聚合页 | local 20s / Redis 5m |
| `mall-product` | `product:sku-images` | `skuId` | 商品图片 | local 30s / Redis 30m |
| `mall-product` | `product:spu-desc` | `spuId` | SPU 描述 | local 30s / Redis 30m |
| `mall-product` | `product:spu-sale-attrs` | `spuId` | 销售属性矩阵 | local 30s / Redis 30m |
| `mall-product` | `product:spu-attr-groups` | `spuId` | 规格属性分组 | local 30s / Redis 30m |
| `mall-coupon` | `coupon:seckill-page` | `relationId` | 秒杀页渲染数据 | local 2s / Redis 5s |
| `mall-coupon` | `coupon:seckill-relation` | `relationId` | 秒杀商品关系 | local 10s / Redis 2m |
| `mall-coupon` | `coupon:seckill-session` | `sessionId` | 秒杀场次基础信息 | local 30s / Redis 5m |
| `mall-ware` | `ware:sku-by-sku` | `skuId` | SKU 分仓库存行 | local 5s / Redis 30s |
| `mall-ware` | `ware:sku-available-stock` | `skuId` | SKU 聚合可售量 | local 5s / Redis 30s |

## 写路径纪律

每个业务模块维护自己的 invalidator：`ProductHotCacheInvalidator`、`PromotionHotCacheInvalidator`、`WareHotCacheInvalidator`。业务 service 不直接拼 Redis key，也不直接删本地 Caffeine；失效只通过 invalidator 进入统一封装。

有事务的写路径必须使用 `*AfterCommit`。原因是缓存不参与数据库事务：事务内提前删缓存，随后事务回滚，会让其他请求回源读到旧值并重新写回缓存；事务提交后再删，才能保证下一次回源看到的是已提交数据。

库存缓存的 TTL 故意很短。它只能用于页面展示和查询接口，不能用于判断能不能下单。`orderLockStock` 可以用缓存枚举候选库存行，但真正是否锁定成功仍由 `UPDATE ... WHERE stock - stock_locked >= count` 的影响行数决定。

## 指标

`MultiLevelCacheClient` 暴露两类指标：

```text
mall_cache_multi_level_requests_total{cache,result}
mall_cache_hot_key_total{cache}
```

`result` 是有界集合，例如 `local_hit`、`redis_hit`、`miss`、`mutex_timeout`、`evict`。不要把业务 ID 放进指标标签；热点 key 明细只写日志，指标只按 cache name 聚合。

## Runbook

缓存命中率下降时，先确认是不是刚发布、批量改价、批量上下架或活动配置变更造成的正常失效。如果只有某个 cache 的 `miss` 抬升，查对应服务日志里的回源 SQL 和 Feign 调用耗时。

出现 `mutex_timeout` 时，说明同一个 key 的回源时间超过等待窗口。先看下游数据库/Feign 是否变慢，再决定是拉长 `lockWait`、预热热点 key，还是拆小聚合对象。

出现 `mall_cache_hot_key_total` 快速增长时，先确认业务是否有大促、爬虫或前端轮询异常。大促热点应优先预热；异常来源应在网关限流或业务限流处理，不要单纯把缓存 TTL 调到很长。

手动清缓存只作为止血动作。正确修复应落在写路径 after-commit 失效、TTL 策略、预热任务或上游流量治理上。

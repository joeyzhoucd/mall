# Mall Payment 本地模拟支付服务

`mall-payment` 是本地支付通道模拟服务。它不连接真实支付宝、微信或信用卡网关，也不落库；当前状态保存在进程内存里，适合本地开发、联调和自动化测试。

## 设计目标

订单服务后续只需要调用一个支付网关地址：

```properties
mall.payment.gateway.base-url=http://mall-payment:9010
```

本地开发时这个地址指向 `mall-payment`；将来接真实支付时，换成真实支付网关或公司内部支付中台地址即可。订单侧应继续使用统一请求/响应模型，不直接散落支付宝、微信、信用卡 SDK 细节。

## 统一接口

### 创建支付

```http
POST /payment/mock/payments
Content-Type: application/json
```

```json
{
  "channel": "alipay",
  "orderSn": "ORD-1001",
  "amount": 88.8,
  "currency": "CNY",
  "subject": "Mall order ORD-1001",
  "notifyUrl": "http://order.mall.com/pay/notify"
}
```

`channel` 支持：

| 通道 | 值 |
| --- | --- |
| 支付宝 | `alipay` |
| 微信支付 | `wechat` |
| 信用卡 | `credit_card` |

返回体统一放在 `payment` 字段里，并包含：

| 字段 | 说明 |
| --- | --- |
| `tradeNo` | 模拟支付平台交易号 |
| `status` | `pending` / `success` / `closed` / `refunded` |
| `payUrl` | 支付跳转或扫码地址 |
| `qrCode` | 扫码支付地址 |
| `prepayId` | 微信风格预支付 ID |
| `providerPayload` | 对应通道的模拟平台原始响应 |
| `signedContent` / `sign` | mock HMAC-SHA256 签名内容和十六进制签名 |
| `idempotent` | 重复创建同一通道同一订单时为 `true` |

### 查询支付

```http
GET /payment/mock/payments/{channel}/{orderSn}
```

### 模拟支付成功

```http
POST /payment/mock/payments/{channel}/{orderSn}/success
```

默认返回 `notify` 字段，用于模拟异步回调给订单服务。

### 模拟支付关闭

```http
POST /payment/mock/payments/{channel}/{orderSn}/close
```

### 退款

```http
POST /payment/mock/refunds
Content-Type: application/json
```

```json
{
  "channel": "wechat",
  "orderSn": "ORD-1001",
  "refundSn": "RF-1001",
  "amount": 10.00,
  "reason": "user request"
}
```

### 下载对账单

```http
GET /payment/mock/reconciliation?date=2026-09-03
Accept: text/csv
```

CSV 字段：

```csv
row_type,reconcile_date,channel,order_sn,trade_no,refund_sn,amount,currency,status,happened_at
```

`row_type=PAYMENT` 表示支付流水，`row_type=REFUND` 表示退款流水。mock 服务按 UTC 日期导出当天发生状态变更的支付流水和当天发生的退款流水。

## 通道格式模拟

除了统一接口，服务还暴露通道风格接口，方便联调外部支付适配层：

| 通道 | 创建接口 |
| --- | --- |
| 支付宝 | `POST /payment/provider-mock/alipay/trade/precreate` |
| 微信支付 | `POST /payment/provider-mock/wechat/pay/transactions/native` |
| 信用卡 | `POST /payment/provider-mock/card/payments` |

这些接口仍复用同一份内存状态和幂等逻辑，只是直接返回对应通道的 `providerPayload`。

## 本地配置

`mall-payment/src/main/resources/application.yml` 默认端口是 `9010`。

```yaml
mall:
  payment:
    mock:
      sign-key: ${PAYMENT_MOCK_SIGN_KEY:mall-payment-local-mock-sign-key}
      alipay-app-id: ${PAYMENT_MOCK_ALIPAY_APP_ID:2026090300000000}
      wechat-app-id: ${PAYMENT_MOCK_WECHAT_APP_ID:wx0000000000000000}
      wechat-mch-id: ${PAYMENT_MOCK_WECHAT_MCH_ID:1900000000}
      gateway-base-url: ${PAYMENT_MOCK_BASE_URL:http://localhost:9010}
```

网关已经加了 `/payment/** -> lb://mall-payment` 路由。

## 订单服务接入

`mall-order` 通过 `mall.payment.gateway.base-url` 调用支付网关：

```yaml
mall:
  payment:
    gateway:
      base-url: ${PAYMENT_GATEWAY_BASE_URL:http://localhost:9010}
      notify-url: ${PAYMENT_NOTIFY_URL:http://order.mall.com/order/payments/notify}
      return-url: ${PAYMENT_RETURN_URL:http://order.mall.com/order/payment.html}
      sign-key: ${PAYMENT_GATEWAY_SIGN_KEY:mall-payment-local-mock-sign-key}
```

订单侧接口：

| 接口 | 说明 |
| --- | --- |
| `POST /order/payments/{orderSn}` | 为订单创建支付单并写入 `oms_payment_info` |
| `GET /order/payments/{channel}/{orderSn}` | 查询支付网关并同步支付信息 |
| `POST /order/payments/notify` | 接收支付回调，验签后幂等推进订单状态 |
| `POST /order/payments/refunds` | 调用支付网关退款并同步支付状态 |

当前幂等先落到独立回调事件表 `oms_payment_notify_event`：订单侧验签并确认订单存在后，用 `channel + tradeNo + tradeStatus` 生成 `event_key`，首次回调写入 `processing`，处理完成后标记为 `processed`；重复事件直接返回幂等结果，不再推进订单状态。若订单支付记录已处于 `success`、`closed` 或 `refunded`，该回调事件会被标记为 `ignored`。

建表脚本见 `docs/sql/order-payment-notify-event.sql`。后续接入 Flyway/Liquibase 时，应把这份脚本纳入订单库版本化迁移。

## 主动查单补偿

`mall-order` 已开启支付主动查单补偿任务。任务定期扫描 `oms_payment_info` 中超过宽限时间仍为 `pending` 且有 `payment_channel` 的支付单，调用支付网关查询接口同步状态；如果网关返回 `success` 或 `closed`，继续复用订单服务已有的 `payOrderSuccess` / `closeOrder` 状态推进入口。

默认配置：

```yaml
mall:
  payment:
    reconciliation:
      enabled: true
      initial-delay-ms: 30000
      fixed-delay-ms: 60000
      stale-after-seconds: 30
      batch-size: 100
```

主动查单需要 `oms_payment_info.payment_channel` 字段，脚本见 `docs/sql/order-payment-info-payment-channel.sql`。后续接入 Flyway/Liquibase 时，应和回调事件表一起纳入订单库版本化迁移。

## 文件对账

订单服务已接入支付对账文件处理：

```http
POST /order/payments/reconciliation/2026-09-03
```

处理流程：

1. `mall-order` 通过 `PaymentGatewayClient` 下载 `/payment/mock/reconciliation?date=...` CSV。
2. `PAYMENT` 行按 `order_sn + channel` 查询 `oms_payment_info`，比对平台交易号、金额、状态和币种。
3. `REFUND` 行按 `refund_sn` 查询 `oms_refund_info`，比对订单号、通道、交易号、金额、状态和币种。
4. 每一行结果写入 `oms_payment_reconciliation_result`，`difference_type=MATCH` 时 `process_status=resolved`，否则 `process_status=pending`。

每日自动对账任务 `PaymentStatementReconciliationTask` 默认关闭，可通过配置打开：

```yaml
mall:
  payment:
    statement-reconciliation:
      enabled: true
      cron: "0 30 1 * * *"
      zone: UTC
```

相关脚本：

| 脚本 | 用途 |
| --- | --- |
| `docs/sql/order-payment-info-payment-channel.sql` | 给 `oms_payment_info` 增加支付通道、币种和主动查单索引 |
| `docs/sql/order-refund-info-reconciliation-fields.sql` | 给 `oms_refund_info` 增加订单号、通道、交易号、退款交易号和币种 |
| `docs/sql/order-payment-reconciliation-result.sql` | 新增支付对账结果表 |

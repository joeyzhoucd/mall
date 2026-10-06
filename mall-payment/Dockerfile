# syntax=docker/dockerfile:1
# =============================================================================
# mall-payment：模拟支付网关（mall-order 的 PaymentGatewayClient 调它）
# =============================================================================
# 2026-10-06 之前这个模块【从没部署过】：没有 Dockerfile，于是 CI 的
# affected-services.sh（按 */Dockerfile 认服务）从不构建它，chart 里也没有它。
# mall-order 的 payment.gateway.base-url 一直是默认的 localhost:9010 —— 后面没有任何东西。
# 没人发现是因为前台付款走的是 mall-order 自带的 /pay/mock/notify，不经过这里；
# 而依赖它的建单/查单/退款/对账在集群里从来没跑通过。
#
# 结构和 mall-auth/Dockerfile 逐段一致（依赖层/源码层分离、CDS 训练、lib 与 app.jar 分层），
# 每一段为什么这么写见那份文件的注释，这里不重复。只有两处不同：
#   - 模块名 / 主类 / 端口
#   - CDS 训练不传 spring.data.redis.host：mall-payment 的依赖树里没有 Redis、
#     RabbitMQ、数据库（实测 mvn dependency:tree，只带了 Consul 和 Config Client）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend

COPY pom.xml .
COPY mall-common/pom.xml mall-common/
COPY mall-test-support/pom.xml mall-test-support/
COPY mall-session-starter/pom.xml mall-session-starter/
COPY mall-mq-starter/pom.xml mall-mq-starter/
COPY mall-admin/pom.xml mall-admin/
COPY mall-auth/pom.xml mall-auth/
COPY mall-cart/pom.xml mall-cart/
COPY mall-config/pom.xml mall-config/
COPY mall-coupon/pom.xml mall-coupon/
COPY mall-gateway/pom.xml mall-gateway/
COPY mall-member/pom.xml mall-member/
COPY mall-order/pom.xml mall-order/
COPY mall-payment/pom.xml mall-payment/
COPY mall-product/pom.xml mall-product/
COPY mall-search/pom.xml mall-search/
COPY mall-thirdparty/pom.xml mall-thirdparty/
COPY mall-ware/pom.xml mall-ware/

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl mall-payment -am dependency:go-offline -DskipTests || true

COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -pl mall-payment -am -DskipTests package -q

FROM eclipse-temurin:21-jre-jammy AS cds
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-payment/target/*.jar /app/app.jar
# 【*-training-only 参数】没有默认值的密钥配置给一个训练专用假值，否则训练在配置阶段就失败、
# 归档变小（实测数字和完整说明见 mall-order/Dockerfile 同一位置）。值不进镜像配置。
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && cd /app/extracted \
 && (timeout 240 java -XX:ArchiveClassesAtExit=/app/app.jsa \
      -cp "app.jar:lib/*" com.mall.payment.MallPaymentApplication \
      --mall.cds.training-exit=true \
      --spring.cloud.consul.enabled=false \
      --spring.cloud.config.enabled=false \
      --management.tracing.export.enabled=false \
      --mall.payment.mock.sign-key=cds-training-only \
      --server.port=0 \
      > /tmp/cds-train.log 2>&1 || true) \
 && (test -f /app/app.jsa && echo "CDS 归档已生成: $(stat -c%s /app/app.jsa) 字节" \
      || (echo "!! CDS 归档未生成。训练日志尾部:" && tail -40 /tmp/cds-train.log && exit 1))

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app/extracted
COPY --from=cds /app/extracted/lib /app/extracted/lib
COPY --from=cds /app/extracted/app.jar /app/extracted/app.jar
COPY --from=cds /app/app.jsa /app/app.jsa
EXPOSE 9010
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-Xlog:cds=warning:stdout", \
  "-cp", "app.jar:lib/*", \
  "com.mall.payment.MallPaymentApplication"]

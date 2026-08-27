# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-config -am -DskipTests package -q

# ---------------------------------------------------------------------------
# 这个服务【刻意不启用 CDS】，其余 11 个都启用了
# ---------------------------------------------------------------------------
# CDS 的训练运行需要「refresh 完成后主动退出 JVM」。Spring Boot 3.2/3.3 的
# spring.context.exit=onRefresh 在 Boot 4.1.1 里已经【不存在】（字节码和配置元数据
# 里都搜不到这个字符串），而且失效是完全静默的 —— 配了等于没配，应用照常完整启动
# 然后永远不退出，Docker 构建一直卡到超时。
#
# 替代方案是 mall-common 里的 CdsTrainingExit（一个 ContextRefreshedEvent 监听器，
# 由 mall.cds.training-exit=true 开启）。但 mall-config【刻意不依赖 mall-common】——
# 它是 Config Server，依赖 mall-common 会引入 spring-cloud-starter-config，
# 导致启动时向自己拉配置、形成循环（理由写在它的 pom 里）。
#
# 为这一个服务再复制一份退出机制不值得：它是最小的模块，启动本来就快，
# CDS 那 4.6 秒的收益在这里意义不大。所以保持普通构建。
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-config/target/*.jar /app/app.jar
EXPOSE 8888
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]

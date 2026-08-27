# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-ware -am -DskipTests package -q

# ---------------------------------------------------------------------------
# CDS（Class Data Sharing）训练阶段
# ---------------------------------------------------------------------------
# 把「解析 class 文件、构建元数据」的启动成本挪到镜像构建期：构建时空跑一次应用，
# 导出共享归档（.jsa），运行时 JVM 直接 mmap，不再逐个解析。
#
# 实测收益（在 mall-coupon 上做的同镜像同应用 A/B，只改启动命令里有无
# -XX:SharedArchiveFile，各测 3-4 次，两组区间不重叠）：
#   无 CDS: 30.2 / 28.9 / 28.3 / 29.0 秒   均值 29.1
#   有 CDS: 23.3 / 25.2 / 25.1 秒          均值 24.5
# 省约 4.6 秒 / 16%。代价：镜像 171 -> 188 MiB，拉取 4m00s -> 4m16s。
#
# 三个容易让这项优化【静默失效】的点，完整推理见 mall-coupon/Dockerfile：
#   1. 必须用 -cp 而不是 -jar。Boot 启动器用自己的 LaunchedClassLoader，
#      CDS 归档不了它加载的类 —— 归档命中率 10% 对 95%。
#   2. 运行时的 -cp 必须和训练时【逐字一致】（含相对/绝对路径），
#      否则 JVM 判定归档不匹配并静默退回。所以两边都 cd 到 /app/extracted 用相对路径。
#   3. 加 -Xlog:cds=warning:stdout，让「归档没生效」变成 pod 日志里的可见警告。
#      没有它的话失效时外部完全看不出来：启动照样成功、只是慢。
#
# 训练运行要断开所有外部依赖，否则会卡在各种连接超时上。注意关闭链路导出的
# 属性名是 management.tracing.export.enabled —— management.tracing.enabled 在
# Boot 4 里是 error 级废弃、压根不会被绑定，写了等于没写且无提示。
FROM eclipse-temurin:21-jre-jammy AS cds
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-ware/target/*.jar /app/app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted &&     cd /app/extracted &&     (java -XX:ArchiveClassesAtExit=/app/app.jsa           -cp "app.jar:lib/*" com.mall.ware.MallWareApplication           --spring.context.exit=onRefresh           --spring.cloud.consul.enabled=false           --spring.cloud.config.enabled=false           --spring.data.redis.host=127.0.0.1           --management.tracing.export.enabled=false           --server.port=0           > /tmp/cds-train.log 2>&1 || true) &&     (test -f /app/app.jsa && echo "CDS 归档已生成: $(stat -c%s /app/app.jsa) 字节"       || (echo "!! CDS 归档未生成。训练日志尾部:" && tail -40 /tmp/cds-train.log && exit 1))

FROM eclipse-temurin:21-jre-jammy
# WORKDIR 和下面的 -cp 必须和训练阶段逐字一致，见上面第 2 点。
WORKDIR /app/extracted
COPY --from=cds /app/extracted /app/extracted
COPY --from=cds /app/app.jsa /app/app.jsa
EXPOSE 12000
ENTRYPOINT ["java",   "-XX:MaxRAMPercentage=75.0",   "-XX:SharedArchiveFile=/app/app.jsa",   "-Xlog:cds=warning:stdout",   "-cp", "app.jar:lib/*",   "com.mall.ware.MallWareApplication"]

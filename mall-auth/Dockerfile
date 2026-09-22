# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend
COPY . .
# 【Maven 本地仓库挂成 BuildKit 缓存】2026-09-22 加，先在 mall-auth 试点。
#
# 起因：这一轮 CI 里 12 个服务并行构建，只有 mall-auth 挂了，
# 错误只有一句 "mvn ... package -q did not complete successfully: exit code 1"，
# 而本地跑同一条命令是成功的。根子在下面这个结构：
#   COPY . .        <- 源码一变，这一层缓存就失效
#   RUN mvn package <- 于是每次都从零下载【全部】依赖
# workflow 虽然配了 GHA 镜像层缓存（cache-from/to type=gha），
# 但它缓存的是【层】，而这一层每次都被 COPY 弄脏，等于没有。
# 12 个服务同时从零拉依赖，偶发一个失败并不奇怪——这次刚好又多了
# spring-retry 这个新依赖。
#
# cache mount 让 ~/.m2/repository 独立于层缓存存在，同一 runner 上重复构建
# 直接复用，本地构建受益最明显。
#
# 【要如实说明它的局限】GHA 每个 job 是全新的 runner，而 type=gha 的层缓存
# 【不包含 cache mount 的内容】，所以对「12 个并行 job 各自首次构建」这个场景
# 帮助有限。真正解决它要把依赖层和源码层分开
# （先 COPY 各模块的 pom.xml 做 dependency:go-offline，再 COPY 源码），
# 那需要逐个复制 12 个 pom，复杂且容易漏，作为独立一项再做。
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -pl mall-auth -am -DskipTests package -q

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
# 【退出机制】必须用 mall.cds.training-exit=true（mall-common 的 CdsTrainingExit）。
# 不要用 spring.context.exit=onRefresh —— 那是 Boot 3.2/3.3 的属性，在 Boot 4.1.1 里
# 已经【不存在】（字节码和配置元数据里都搜不到这个字符串），而且失效完全静默：
# 配了等于没配，应用照常完整启动然后【永远不退出】，Docker 构建一直卡到超时。
# 实际后果：12 个服务的镜像构建全部卡住，而 CI 矩阵里一个服务失败就会挡住
# 「更新镜像 tag 到 mall-deploy」那一步，于是整个部署停摆 —— 表现却只是
# 「CI 跑了 40 分钟没有任何产出」，完全不指向某个配置属性被移除了。
#
# 【timeout 240 是兜底】退出机制万一又失效（配置属性被上游移除这种事已经发生过），
# 构建会在 4 分钟内明确失败而不是吊满 job 的 25 分钟超时：超时后拿不到归档，
# 下面的 test -f 就会 exit 1。实测正常的训练运行约 100 秒（含写归档），4 分钟很宽裕。
# 注意 timeout 发 SIGTERM 时【归档不会生成】（实测过），所以它只能当兜底、
# 不能当正常的结束方式 —— 正常结束必须靠进程自己退出。
#
# 【训练运行失败是可以接受的】构建环境里没有 Redis / MySQL / RabbitMQ，所以依赖
# 它们的服务会在 refresh 完成前就报错退出（例如 Spring Session 的
# springSessionRepositoryFilter 建不起来）。这没关系：-XX:ArchiveClassesAtExit 在
# JVM 退出时就写归档，失败前加载的类占了绝大多数 —— 实测 mall-coupon 这样产出
# 76MB 归档（完整跑完 83MB），而那 4.6 秒的收益正是在这种情况下测出来的。
# 真正致命的只有「不失败也不退出」，那才是上面那个失效属性造成的。
#
# 另外三个容易让这项优化【静默失效】的点，完整推理见 mall-coupon/Dockerfile：
#   1. 必须用 -cp 而不是 -jar。Boot 启动器用自己的 LaunchedClassLoader，
#      CDS 归档不了它加载的类 —— 归档命中率 10% 对 95%。
#   2. 运行时的 -cp 必须和训练时【逐字一致】（含相对/绝对路径），
#      否则 JVM 判定归档不匹配并静默退回。所以两边都 cd 到 /app/extracted 用相对路径。
#   3. 加 -Xlog:cds=warning:stdout，让「归档没生效」变成 pod 日志里的可见警告。
#
# 关闭链路导出的属性名是 management.tracing.export.enabled ——
# management.tracing.enabled 在 Boot 4 里同样是 error 级废弃、压根不会被绑定。
FROM eclipse-temurin:21-jre-jammy AS cds
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-auth/target/*.jar /app/app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && cd /app/extracted \
 && (timeout 240 java -XX:ArchiveClassesAtExit=/app/app.jsa \
      -cp "app.jar:lib/*" com.mall.auth.MallAuthApplication \
      --mall.cds.training-exit=true \
      --spring.cloud.consul.enabled=false \
      --spring.cloud.config.enabled=false \
      --spring.data.redis.host=127.0.0.1 \
      --management.tracing.export.enabled=false \
      --server.port=0 \
      > /tmp/cds-train.log 2>&1 || true) \
 && (test -f /app/app.jsa && echo "CDS 归档已生成: $(stat -c%s /app/app.jsa) 字节" \
      || (echo "!! CDS 归档未生成。训练日志尾部:" && tail -40 /tmp/cds-train.log && exit 1))

FROM eclipse-temurin:21-jre-jammy
# WORKDIR 和下面的 -cp 必须和训练阶段逐字一致，见上面第 2 点。
WORKDIR /app/extracted
COPY --from=cds /app/extracted /app/extracted
COPY --from=cds /app/app.jsa /app/app.jsa
EXPOSE 20000
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-Xlog:cds=warning:stdout", \
  "-cp", "app.jar:lib/*", \
  "com.mall.auth.MallAuthApplication"]

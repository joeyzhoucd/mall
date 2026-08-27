# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend
COPY . .
RUN mvn -pl mall-coupon -am -DskipTests package -q

# ---------------------------------------------------------------------------
# CDS（Class Data Sharing）训练阶段
# ---------------------------------------------------------------------------
# 目的：把「解析 class 文件、构建元数据」这部分启动成本挪到镜像构建期。
# 做法是在构建时空跑一次应用，把加载过的类导出成共享归档（.jsa），
# 运行时 JVM 直接 mmap 这份归档，不用再逐个解析。
#
# 【必须用 -cp 而不是 -jar】—— 这是本地实测出来的关键点：
#   -jar 形式（Spring Boot 启动器）: 归档命中率只有 10%（1310/12700 个类）
#   -cp  形式（标准 classpath）    : 命中率 95%（12011/12513 个类）
# 原因是 Boot 的启动器用自己的 LaunchedClassLoader 加载 lib/*.jar，
# 而 CDS 只能归档标准 app classloader 加载的类 —— 转储时会打一堆
# "Unsupported location" 警告，但【不影响构建成功】，很容易被划过去。
# 所以先用 jarmode=tools 解包成「普通 jar + lib 目录」，再用 -cp 直接跑主类。
#
# 【训练运行要断开所有外部依赖】否则它会卡在各种连接超时上：
#   - consul/config 关掉，不然注册和拉配置都会失败重试
#   - Redis 指向 127.0.0.1：连不上时是「立刻拒绝」而不是「10 秒超时」，
#     这一条是刻意的 —— 指向一个不可达的外网地址会让训练运行多等几十秒
#   - 链路导出关掉，不然 OTLP 的 RetryInterceptor 会反复重试拖住退出。
#     注意属性名是 management.tracing.export.enabled ——
#     直觉会写 management.tracing.enabled，但那个在 Boot 4 里是【error 级废弃】，
#     也就是压根不会被绑定，写了等于没写，而且不会有任何提示。
#     命令行参数不在 ConfigMetadataTest 的检查范围内，所以这里只能靠查元数据确认。
#   - spring.context.exit=onRefresh：refresh 完就退出，不跑 ApplicationRunner
#     （所以启动预热那几个 runner 不会执行，训练运行不需要真的建连接）
#
# 训练运行失败不能让构建失败（|| true）：拿不到归档只是退化成没有 CDS，
# 而运行时 -XX:SharedArchiveFile 指向不存在的文件时 JVM 默认只是警告并继续
# （-Xshare:auto）。宁可少一层优化，不要让镜像构建变脆。
FROM eclipse-temurin:21-jre-jammy AS cds
WORKDIR /app
COPY --from=build /workspace/mall-backend/mall-coupon/target/*.jar /app/app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted && \
    cd /app/extracted && \
    (java -XX:ArchiveClassesAtExit=/app/app.jsa \
          -cp "app.jar:lib/*" com.mall.coupon.MallCouponApplication \
          --spring.context.exit=onRefresh \
          --spring.cloud.consul.enabled=false \
          --spring.cloud.config.enabled=false \
          --spring.data.redis.host=127.0.0.1 \
          --management.tracing.export.enabled=false \
          --server.port=0 \
          > /tmp/cds-train.log 2>&1 || true) && \
    (test -f /app/app.jsa && echo "CDS 归档已生成: $(stat -c%s /app/app.jsa) 字节" \
      || (echo "!! CDS 归档未生成，将退化为无 CDS 运行。训练日志尾部:" && tail -30 /tmp/cds-train.log))

FROM eclipse-temurin:21-jre-jammy
# WORKDIR 必须是 /app/extracted，且下面的 -cp 必须写成相对路径 —— 要和训练阶段
# 【逐字一致】。CDS 会校验 classpath 字符串：相对 vs 绝对、或者条目顺序不同，
# 都会让 JVM 判定归档不匹配并【静默】退回不使用共享归档。
# 这是最容易让这项优化悄悄失效的一处，所以下面还加了 -Xlog:cds=warning。
WORKDIR /app/extracted
COPY --from=cds /app/extracted /app/extracted
# 归档不存在时这条 COPY 会失败，所以上一阶段用 test -f 兜了一层：
# 真的没生成时构建会在这里报错，比悄悄跑一个没有 CDS 的镜像更好 ——
# 那样谁都不会发现优化其实没生效。
COPY --from=cds /app/app.jsa /app/app.jsa
EXPOSE 10000
# 运行时的 -cp 必须和训练时【完全一致】，否则 JVM 会判定归档不匹配、
# 静默退回不使用共享归档（只在 -Xlog:cds 里能看到原因）。
# -Xlog:cds=warning 让「归档不匹配」在 pod 日志里变成一条可见的警告，
# 而不是静默退回。没有它的话，这项优化失效时【外部完全看不出来】——
# 启动照样成功、只是慢，而慢本来就有很多别的原因，根本不会想到是 CDS 没生效。
# 想进一步确认归档真的在用（会很吵，临时排查才开）：
#   java -Xlog:class+load=info ... | grep -c "source: shared"
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-Xlog:cds=warning:stdout", \
  "-cp", "app.jar:lib/*", \
  "com.mall.coupon.MallCouponApplication"]

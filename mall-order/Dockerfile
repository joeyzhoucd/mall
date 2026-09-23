# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend

# =============================================================================
# 【依赖层和源码层分开】2026-09-22
# =============================================================================
# 【要解决什么】原来是这个结构：
#     COPY . .
#     RUN mvn -pl mall-order -am -DskipTests package -q
# COPY 在 RUN 前面，所以【改一行 Java 代码就让下面那层缓存失效】，
# 每次构建都要把全部依赖重新下载一遍。
# workflow 配了 GHA 层缓存（cache-from/to type=gha）但完全帮不上忙——
# 它缓存的是层，而这一层每次都被 COPY 弄脏。
#
# 后果是实测到的：连续两轮 CI，12 个服务并行构建，各随机挂掉一个
# （一次 mall-auth、一次 mall-thirdparty），错误只有一句
# "mvn ... package -q did not complete successfully: exit code 1"，
# 而本地跑同一条命令成功。12 个 job 同时从零拉依赖，偶发失败并不奇怪。
#
# 而且后果不只是「重跑一次」：update-deploy 依赖【全部】build 成功，
# 一个服务挂掉会让同轮其余【已经构建成功】的服务都拿不到 tag 更新，
# 改动静默留在 ghcr.io 里不上线。两轮都是靠人盯 tag 才发现的。
#
# 【怎么改】先只复制 pom，把依赖下载单独做成一层。
# pom 很少变，所以这一层能被 type=gha 长期命中；
# 改 Java 代码只会让后面的源码层失效，依赖不用重下。
#
# 【为什么复制全部 17 个模块的 pom，而不只是本服务依赖的那几个】
# （举例：mall-auth 的 reactor 链只有 mall / mall-common / mall-test-support /
#  mall-session-starter / mall-auth 五个）
# 为了让所有服务的 Dockerfile 这一段【逐字一样】，只有最后的 -pl 不同。
# 各自只列自己依赖的模块，看着更精确，但每加一个模块就要改 12 处、
# 漏一处就是一次莫名其妙的构建失败——那种错误还特别难归因。
# 代价是任何模块的 pom 变化都会让所有服务的依赖层失效，
# 而 pom 变动的频率远低于源码，这个代价是划算的。
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

# 【go-offline 失败不中断构建，这是刻意的】
# dependency:go-offline 覆盖不到所有东西（某些插件只在真正执行阶段才解析依赖），
# 而且在多模块 reactor 里偶尔会因为内部模块尚未安装而报错。
# 它在这里的作用是【尽量预热缓存】，不是「保证一个都不缺」——
# 缺的那部分下面 package 会补下。所以用 || true 兜住：
# 让它失败就中断，等于把一个优化变成了新的脆弱点。
#
# cache mount 一并保留：它对同一 runner 内的重复构建和本地构建有效，
# 和层缓存是互补的两件事。
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -pl mall-order -am dependency:go-offline -DskipTests || true

# 到这里依赖已经在缓存里。现在才复制源码——改 Java 代码只会让这一层往下失效。
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -pl mall-order -am -DskipTests package -q

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
COPY --from=build /workspace/mall-backend/mall-order/target/*.jar /app/app.jar
RUN java -Djarmode=tools -jar app.jar extract --destination extracted \
 && cd /app/extracted \
 && (timeout 240 java -XX:ArchiveClassesAtExit=/app/app.jsa \
      -cp "app.jar:lib/*" com.mall.order.MallOrderApplication \
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
# =============================================================================
# 【lib 和 app.jar 必须分两层，顺序不能反】2026-09-22
# =============================================================================
# extracted 里只有两样东西（在跑着的容器里核过）：
#     lib/      73MB，190 个 jar —— 依赖，pom 不变它就不变
#     app.jar   36KB          —— 你的代码，每次提交都变
# 原来一条 COPY 把两者压成一层，于是【改一行 Java 代码，节点要重拉 64.5MB】，
# 其中 99.95% 是根本没变的依赖。
#
# 这和构建阶段那个「COPY . . 写在 RUN mvn 前面」是同一个问题的另一半：
# 上次（同一天）只修了构建缓存，运行时镜像的层没分。
#
# 【顺序不能反】Docker 的层是有序的：后面的层变了不影响前面的层，
# 反过来前面的层一变，后面全部失效。lib 不变、app.jar 每次变，
# 所以 lib 必须在前。写反了等于没拆。
#
# 实测依据（mall-cart:22beedb 的镜像层，压缩后）：
#     94.4MB  eclipse-temurin:21-jre-jammy   14 个服务共享，每节点只拉一次
#     64.5MB  COPY /app/extracted            <- 就是这一层，现在拆开
#     16.8MB  COPY /app/app.jsa              CDS 归档，每次构建都重生成，拆不动
# 拆完之后纯代码改动的单服务增量：81.3MB -> 约 16.8MB。
COPY --from=cds /app/extracted/lib /app/extracted/lib
COPY --from=cds /app/extracted/app.jar /app/extracted/app.jar
COPY --from=cds /app/app.jsa /app/app.jsa
EXPOSE 9000
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:SharedArchiveFile=/app/app.jsa", \
  "-Xlog:cds=warning:stdout", \
  "-cp", "app.jar:lib/*", \
  "com.mall.order.MallOrderApplication"]

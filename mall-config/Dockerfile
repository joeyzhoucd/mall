# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace/mall-backend

# =============================================================================
# 【依赖层和源码层分开】2026-09-22
# =============================================================================
# 【要解决什么】原来是这个结构：
#     COPY . .
#     RUN mvn -pl mall-config -am -DskipTests package -q
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
    mvn -B -pl mall-config -am dependency:go-offline -DskipTests || true

# 到这里依赖已经在缓存里。现在才复制源码——改 Java 代码只会让这一层往下失效。
COPY . .
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -pl mall-config -am -DskipTests package -q

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

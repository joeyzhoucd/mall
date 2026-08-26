#!/usr/bin/env bash
#
# 校验每个「可部署模块」都产出了可执行的 fat jar。
#
# 为什么需要这个检查
# ------------------
# 本项目不继承 spring-boot-starter-parent（只 import 了 spring-boot-dependencies BOM），
# 而 spring-boot-maven-plugin 的 repackage 目标（把普通 jar 变成可执行 fat jar）
# 平时正是由那个 parent 帮你绑定的。所以这里【每个服务模块的 pom 都必须自己声明一个
# repackage execution】。
#
# 漏写的后果极其隐蔽：mvn 构建完全成功、target 下也确实有 jar，只是那个 jar 里没有
# BOOT-INF/lib、MANIFEST 里也没有 Main-Class，是个普通库 jar。要一直等到镜像启动时
# 才会报 "no main manifest attribute"。新增 mall-admin 时就漏了，最后是靠 jar 大小
# （68MB 对 72KB）才发现的 —— 而"构建成功 + 有 jar"这两个信号当时都是绿的。
#
# 判据
# ----
# 同时满足「有 Dockerfile」和「有 pom.xml」的目录才检查：
#   - 有 Dockerfile 等价于"这个模块要作为服务跑起来"，比维护一份白名单更不容易和现实脱节；
#   - 但光有 Dockerfile 不够 —— static-assets 是 nginx 镜像、elasticsearch-ik 是装插件的
#     ES 镜像，它们有 Dockerfile 却不是 Maven 模块，本来就不产出 jar。
#
# 用法（在 mall-backend 目录下）
#   mvn -DskipTests package && .github/scripts/verify-fat-jars.sh
#
set -uo pipefail

fail=0
checked=0

for dockerfile in */Dockerfile; do
    module=$(dirname "$dockerfile")

    if [ ! -f "$module/pom.xml" ]; then
        echo "跳过 $module（有 Dockerfile 但不是 Maven 模块）"
        continue
    fi

    jar=$(find "$module/target" -maxdepth 1 -name '*.jar' ! -name '*.original' 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
        echo "错误：$module 没有产出 jar（先跑 mvn -DskipTests package）"
        fail=1
        continue
    fi

    libs=$(unzip -l "$jar" 2>/dev/null | grep -c 'BOOT-INF/lib/.*\.jar')
    main=$(unzip -p "$jar" META-INF/MANIFEST.MF 2>/dev/null | grep -c '^Main-Class:')

    if [ "$libs" -eq 0 ] || [ "$main" -eq 0 ]; then
        echo "错误：$module 的 jar 不是可执行 fat jar（BOOT-INF/lib=$libs，Main-Class=$main）"
        echo "      检查 $module/pom.xml 里 spring-boot-maven-plugin 有没有声明 repackage execution。"
        fail=1
    else
        printf "通过：%-18s %s 个依赖\n" "$module" "$libs"
    fi
    checked=$((checked + 1))
done

if [ "$checked" -eq 0 ]; then
    # 一个模块都没检查到，说明脚本本身没在起作用（跑错目录了、或者目录结构变了）。
    # 这种情况必须当成失败 —— 否则会得出"全部通过"的假结论，
    # 而那正是这个脚本要防的那类问题。
    echo "错误：没有检查到任何模块。请确认在 mall-backend 目录下执行。"
    exit 1
fi

echo "共检查 $checked 个模块。"
exit $fail

#!/usr/bin/env bash
#
# 算出一次改动影响哪些【可部署服务】，每行一个，输出到 stdout。
#
# 用法:
#   .github/scripts/affected-services.sh <base-sha> <head-sha>
#   .github/scripts/affected-services.sh --all          # 强制全量
#
# ---------------------------------------------------------------------------
# 为什么需要它
# ---------------------------------------------------------------------------
# CI 原来只把 values.yaml 里【一个】 image.tag 推到新 commit，而 12 个服务
# 全都引用它 —— 于是改一个模块，整集群 12 个服务全部重新拉镜像、全部滚动。
#
# 实测代价（2026-09-08，单个节点单个镜像）：
#   Successfully pulled ... in 5m42s (19m49s including waiting)
# 传输 5m42s，排队 14 分钟 —— 排队是因为一个节点要同时拉 8 个约 200MB 的镜像。
# 一次只该动 1 个服务的提交，实际造成了小时级的集群 churn。
#
# ---------------------------------------------------------------------------
# 失败方向：宁可多构建，绝不少构建
# ---------------------------------------------------------------------------
# 漏判的后果是【某个服务静默继续跑旧代码】—— 没有任何报错，
# 而且和"部署成功"长得一模一样。这个仓库在这类静默失效上已经吃过很多次亏。
# 所以：任何无法归类的改动一律当作"影响全部"，而不是"影响无"。
#
# ---------------------------------------------------------------------------
# 影响面从【真实的 pom 依赖】算，不写映射表
# ---------------------------------------------------------------------------
# 用 mvn -pl <改动的模块> -amd（also-make-dependents）让 Maven 自己算下游闭包。
# 手写一张"mall-mq-starter -> coupon/order/ware"的表迟早和 pom 漂移，
# 而漂移的方向恰好是漏判。
#
# 实测验证（本地 mvn -o -pl X -amd validate）：
#   mall-mq-starter -> mall-coupon mall-order mall-ware
#   mall-product    -> mall-product
#   mall-common     -> 全部（但【不含 mall-config】—— 它不依赖 mall-common，
#                      是个裸的 Spring Cloud Config Server。这正是不该手写表的例子：
#                      凭直觉写"改 common 就全量"会多刷一个服务。）
#
set -euo pipefail

cd "$(dirname "$0")/../.."

# 有 Dockerfile 的才是可部署服务。其中两个不是 Maven 模块，单独按目录判定。
ALL_SERVICES=$(for d in */Dockerfile; do echo "${d%/Dockerfile}"; done | sort)
NON_MAVEN="static-assets elasticsearch-ik"

emit_all() {
  echo "$ALL_SERVICES"
  exit 0
}

if [ "${1:-}" = "--all" ]; then
  echo "[affected] 强制全量" >&2
  emit_all
fi

BASE="${1:-}"
HEAD="${2:-HEAD}"

# base 拿不到（首次推送、force push、workflow_dispatch）时没法比较 -> 全量。
if [ -z "$BASE" ] || [ "$BASE" = "0000000000000000000000000000000000000000" ] \
   || ! git cat-file -e "$BASE^{commit}" 2>/dev/null; then
  echo "[affected] base 不可用（$BASE），按全量处理" >&2
  emit_all
fi

CHANGED=$(git diff --name-only "$BASE" "$HEAD")
if [ -z "$CHANGED" ]; then
  echo "[affected] 没有文件改动" >&2
  exit 0
fi

echo "[affected] 改动的文件：" >&2
echo "$CHANGED" | sed 's/^/  /' >&2

MODULES=""
SERVICES=""

while IFS= read -r f; do
  [ -z "$f" ] && continue
  top="${f%%/*}"

  case "$f" in
    # 构建本身变了 -> 全部。workflow 改了必须让所有镜像都按新逻辑重建一次。
    .github/*|.mvn/*|pom.xml|mvnw|mvnw.cmd)
      echo "[affected] $f 影响构建本身 -> 全量" >&2
      emit_all
      ;;
    # 纯文档。workflow 的 paths-ignore 已经挡掉"全是文档"的推送，
    # 这里再挡一次是为了"文档 + 代码"混合提交时不把文档算成影响面。
    docs/*|*.md|.gitattributes|.gitignore|LICENSE)
      continue
      ;;
  esac

  # 非 Maven 的两个服务：按目录直接命中。
  hit_non_maven=0
  for s in $NON_MAVEN; do
    if [ "$top" = "$s" ]; then
      SERVICES="$SERVICES $s"
      hit_non_maven=1
      break
    fi
  done
  [ "$hit_non_maven" = "1" ] && continue

  # Maven 模块：目录里有 pom.xml 就算。
  if [ -f "$top/pom.xml" ]; then
    MODULES="$MODULES $top"
    continue
  fi

  # 归不了类 -> 全量。这是刻意的保守：见文件头"失败方向"。
  echo "[affected] 无法归类的改动 $f -> 全量" >&2
  emit_all
done <<< "$CHANGED"

# 让 Maven 算下游闭包，再和"可部署服务"求交集。
if [ -n "$MODULES" ]; then
  PL=$(echo $MODULES | tr ' ' '\n' | sort -u | paste -sd, -)
  echo "[affected] 改动的模块：$PL" >&2
  # validate 是最轻的生命周期阶段，只解析 reactor 不编译。
  #
  # 【刻意不加 -o】离线模式在 CI 里会失败：这个 job 的 ~/.m2 是空的，
  # 连父 POM 和 validate 用到的少量插件都解析不了。那会落到 emit_all ——
  # 安全但等于这个脚本白写。本地跑的时候依赖都在缓存里，联网也是秒级。
  #
  # 失败时（pom 语法错、模块不在 reactor 里）不要静默当成"影响无"，那正是漏判。
  if ! REACTOR=$(mvn -B -pl "$PL" -amd validate 2>/dev/null | sed -n 's/^\[INFO\] Building \([^ ]*\) .*/\1/p'); then
    echo "[affected] mvn 求闭包失败 -> 全量" >&2
    emit_all
  fi
  if [ -z "$REACTOR" ]; then
    echo "[affected] mvn 没有输出 reactor -> 全量" >&2
    emit_all
  fi
  for m in $REACTOR; do
    for s in $ALL_SERVICES; do
      [ "$m" = "$s" ] && SERVICES="$SERVICES $s"
    done
  done
fi

echo $SERVICES | tr ' ' '\n' | grep -v '^$' | sort -u

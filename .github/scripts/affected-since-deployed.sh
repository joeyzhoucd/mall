#!/usr/bin/env bash
#
# 按【每个服务当前部署的镜像 tag】算出需要重建的服务，每行一个，输出到 stdout。
#
# 用法:
#   .github/scripts/affected-since-deployed.sh <deployed-tags-file> <head-sha>
#
# deployed-tags-file 每行「服务名 sha」，由 CI 从 mall-deploy 的
# charts/mall/values.yaml 的 imageTags 导出（yq），本地测试可以手写。
#
# ---------------------------------------------------------------------------
# 为什么不用 github.event.before
# ---------------------------------------------------------------------------
# 原来 plan 算的是 affected-services.sh <event.before> <sha>，即「这次推送改了什么」。
# 问题在于：上一次推送的构建失败时，它的改动【没有产出镜像、tag 没前进】，
# 而下一次推送的 before 已经越过了它 —— 那些改动从此不在任何一次 diff 里，
# 静默永远不上线，之后的 CI 还是绿的。
#
# 实测发生过（2026-10-09）：67b3876 改了 mall-common + mall-admin（删 HS256），
# 那次 CI 挂在集成测试；修复提交 9cc0d41 只动了网关的测试，
# plan 只算出 mall-gateway —— 另外 11 个服务停在旧镜像上，没有任何报错。
#
# 现在每个服务的起点是【它在 mall-deploy 里的 tag】，也就是它最后一次成功上线的提交。
# 构建失败只是让 tag 不前进，下一次推送自然会把它带上。
# 判定复用 affected-services.sh（pom 依赖闭包 + 保守全量），只是按起点分组各跑一次。
#
# 失败方向和 affected-services.sh 一样：宁可多构建。
#   - 某个服务在文件里没有 tag          -> 重建它
#   - tag 不是这个仓库里的提交（force push 等）-> affected-services.sh 判全量 -> 重建它
#   - 任意一组判定出错                  -> 整体失败（set -e），不输出半份结果
#
set -euo pipefail

TAGS_FILE="${1:?用法: $0 <deployed-tags-file> <head-sha>}"
HEAD="${2:?用法: $0 <deployed-tags-file> <head-sha>}"

cd "$(dirname "$0")/../.."
SCRIPT=.github/scripts/affected-services.sh

ALL_SERVICES=$(for d in */Dockerfile; do echo "${d%/Dockerfile}"; done | sort)

tag_of() {
  awk -v s="$1" '$1 == s { print $2; exit }' "$TAGS_FILE"
}

RESULT=""
BASES=""
for s in $ALL_SERVICES; do
  t=$(tag_of "$s")
  if [ -z "$t" ] || [ "$t" = "null" ]; then
    echo "[since-deployed] $s 没有已部署 tag -> 重建" >&2
    RESULT="$RESULT $s"
  else
    BASES="$BASES $t"
  fi
done

for base in $(echo $BASES | tr ' ' '\n' | sort -u); do
  # 必须用 if 而不是 `[ ... ] && echo`：后者在最后一个服务不匹配时让整个循环返回 1，
  # set -e 会在这次赋值上【静默退出】，stdout 为空 —— 看起来就是「没有服务需要重建」。
  # 第一版就是这么写的，本地对照测试时抓到。
  members=$(for s in $ALL_SERVICES; do if [ "$(tag_of "$s")" = "$base" ]; then echo "$s"; fi; done)
  echo "[since-deployed] 起点 ${base:0:7}：$(echo $members)" >&2
  # 子 shell 失败要传出来，不能被命令替换吞掉 —— 吞掉等于把「判定出错」当成「影响无」。
  affected=$(bash "$SCRIPT" "$base" "$HEAD")
  for s in $members; do
    if echo "$affected" | grep -qx "$s"; then
      RESULT="$RESULT $s"
    fi
  done
done

echo $RESULT | tr ' ' '\n' | grep -v '^$' | sort -u || true

# 商品搜索的中文分词（ik_smart/ik_max_word）需要 IK 分词插件，官方 Elasticsearch
# 镜像不带这个插件——单独打一个带插件的镜像，版本号要和 ES 本体严格对应（8.11.1）。
# infinilabs/analysis-ik 是原 medcl/elasticsearch-analysis-ik 的延续维护仓库。
# 注意:infinilabs/analysis-ik 已不再通过 GitHub Releases 分发插件包(仓库 README 有说明),
# 改用 get.infini.cloud 分发,URL 末尾版本号需与 ES 本体版本严格对应。
FROM elasticsearch:8.11.1
RUN bin/elasticsearch-plugin install --batch https://get.infini.cloud/elasticsearch/analysis-ik/8.11.1

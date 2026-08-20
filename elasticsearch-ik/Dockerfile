# 商品搜索的中文分词（ik_smart/ik_max_word）需要 IK 分词插件，官方 Elasticsearch
# 镜像不带这个插件——单独打一个带插件的镜像，版本号要和 ES 本体严格对应（8.11.1）。
# infinilabs/analysis-ik 是原 medcl/elasticsearch-analysis-ik 的延续维护仓库。
FROM elasticsearch:8.11.1
RUN bin/elasticsearch-plugin install --batch https://github.com/infinilabs/analysis-ik/releases/download/8.11.1/elasticsearch-analysis-ik-8.11.1.zip

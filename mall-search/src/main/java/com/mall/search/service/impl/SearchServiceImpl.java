package com.mall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.KnnQuery;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.MsearchResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.mall.search.client.EmbeddingClient;
import com.mall.search.config.SearchFusionProperties;
import com.mall.search.service.SearchService;
import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;
import com.mall.search.vo.SkuEsModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "product";
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int PAGE_NAV_SIZE = 5;

    /** 商品标题的向量字段，由 mall-deploy 的建索引脚本写入，维度必须和模型一致（bge-small-zh-v1.5 = 512）。 */
    private static final String VECTOR_FIELD = "titleVector";
    /**
     * knn 的 k 上限。翻页越深 k 越大，而 HNSW 的开销随 k 上升，
     * 所以设个天花板：翻到第 13 页之后语义召回不再加深，只靠关键词那一路补。
     * 真实用户几乎不会翻到那么深，为此让前面每一页都变慢不划算。
     */
    private static final int MAX_KNN_K = 200;
    /** num_candidates 的上限，ES 本身也限制在 10000。 */
    private static final int MAX_KNN_CANDIDATES = 1000;

    /**
     * 返回给页面的字段白名单。
     * <p><b>刻意不含 {@code titleVector}</b>：512 个浮点数按 JSON 文本传回来，
     * 每页 16 条就是 8000 多个数字，白白撑大响应体和反序列化开销，而页面一个都用不上。
     */
    private static final List<String> SOURCE_FIELDS = List.of(
            "skuId", "skuTitle", "skuPrice", "skuImg", "saleCount", "brandName", "brandImg", "categoryName");

    @Autowired
    private ElasticsearchClient esClient;

    @Autowired
    private SearchFusionProperties fusionProperties;

    /**
     * 查询词向量化。<b>它的 embed() 失败时返回 null 而不抛异常</b>，
     * 所以这里不需要任何 try/catch —— 拿到 null 就退回纯关键词检索。
     */
    @Autowired
    private EmbeddingClient embeddingClient;

    @Override
    public SearchResult search(SearchParam param) throws IOException {
        try {
            // 【向量化放在这里，不在 buildSearchRequest 里】RRF 模式要发三路请求，
            // 同一个向量得用三次；放在构建请求里会变成调三次 TEI。
            float[] queryVector = param.hasKeyword() ? embeddingClient.embed(param.getKeyword()) : null;

            // queryVector == null 时两种模式没有区别（都退化成纯关键词检索），
            // 走默认路径即可，省掉 RRF 那两路多余的请求。
            if (fusionProperties.isRrf() && queryVector != null) {
                return searchWithRrf(param, queryVector);
            }
            return searchWithScoreSum(param, queryVector);
        } catch (Exception e) {
            log.error("ES检索异常", e);
            throw e;
        }
    }

    /**
     * 默认模式：ES 原生 {@code query} + {@code knn} 单请求，两路分数相加。
     *
     * <p>实测在当前数据上优于 RRF（38/40 vs 34/40，见 {@link SearchFusionProperties}）。
     * 原因是它对<b>并集里每个文档</b>都算两个分数，而 RRF 只看各自 top-window 内的排名。
     */
    private SearchResult searchWithScoreSum(SearchParam param, float[] queryVector) throws IOException {
        SearchRequest request = buildSearchRequest(param, queryVector);
        log.info("搜索请求DSL: {}", request);
        SearchResponse<SkuEsModel> response = esClient.search(request, SkuEsModel.class);
        TotalHits totalHits = response.hits().total();
        long total = totalHits != null ? totalHits.value() : 0L;
        log.info("搜索响应: 模式=score-sum, 总命中数={}, 耗时={}ms", total, response.took());
        return buildSearchResult(response.hits().hits(), total, response.aggregations(), param);
    }

    /**
     * RRF 模式：两路各自检索，按<b>排名</b>融合。默认不启用，理由见
     * {@link SearchFusionProperties}。
     *
     * <h3>为什么要发三路，而不是两路</h3>
     * 前两路是融合用的（关键词一路、向量一路，各取排名）。
     * <b>第三路专门取聚合</b>，因为左侧筛选面板必须覆盖两路的并集：
     * 只用关键词那一路的聚合，会出现「搜电饭锅有 16 个结果，但筛选面板是空的」
     * —— 那些结果全是向量召回的，关键词一路命中 0 条。
     * 第三路用 {@code size: 0}，不取文档只取聚合，开销很小。
     * <p>
     * 三路装在一个 {@code _msearch} 里，<b>网络往返仍然是 1 次</b>。
     *
     * <h3>代价</h3>
     * 要把两路各 window 条文档传回应用层再融合。window=200 时是 400 条，
     * 而 score-sum 模式只传当前页的 16 条。这也是它默认关闭的原因之一。
     */
    private SearchResult searchWithRrf(SearchParam param, float[] queryVector) throws IOException {
        int pageSize = param.resolvePageSize(DEFAULT_PAGE_SIZE);
        int from = (param.resolvePageNum() - 1) * pageSize;
        // 【窗口至少要够到当前页】不然翻到后面会凭空少结果，
        // 而且是「第 1 页正常、第 3 页变少」这种不容易发现的少。
        int window = Math.max(fusionProperties.rrfWindow(), from + pageSize);
        int rrfK = fusionProperties.rrfK();

        List<Query> filters = buildFilters(param);
        List<Float> vector = toFloatList(queryVector);
        SourceConfig source = SourceConfig.of(sc -> sc.filter(f -> f.includes(SOURCE_FIELDS)));
        Highlight highlight = new Highlight.Builder()
                .fields("skuTitle", h -> h.preTags("<span class='keyword'>").postTags("</span>"))
                .build();

        Query keywordQuery = Query.of(q -> q.bool(b -> b
                .must(m -> m.multiMatch(mm -> mm
                        .fields("skuTitle", "brandName", "categoryName")
                        .query(param.getKeyword())))
                .filter(filters)));
        // num_candidates 必须 >= k，否则 ES 直接报 400
        int numCandidates = Math.max(window, Math.min(MAX_KNN_CANDIDATES, window * 2));
        KnnQuery knn = KnnQuery.of(kn -> kn
                .field(VECTOR_FIELD)
                .queryVector(vector)
                .k(window)
                .numCandidates(numCandidates)
                .filter(filters));

        MsearchResponse<SkuEsModel> resp = esClient.msearch(m -> m
                .index(INDEX_NAME)
                // 路 1：纯关键词，拿 BM25 排名。带高亮——融合后展示要用它
                .searches(s -> s.header(h -> h).body(b -> b
                        .size(window).query(keywordQuery).highlight(highlight).source(source)))
                // 路 2：纯向量，拿 kNN 排名
                .searches(s -> s.header(h -> h).body(b -> b
                        .size(window).knn(knn).source(source)))
                // 路 3：只为聚合，size=0 不取文档
                .searches(s -> s.header(h -> h).body(b -> b
                        .size(0).query(keywordQuery).knn(knn).aggregations(buildAggregations())))
        , SkuEsModel.class);

        List<Hit<SkuEsModel>> keywordHits = itemHits(resp, 0);
        List<Hit<SkuEsModel>> vectorHits = itemHits(resp, 1);
        Map<String, Aggregate> aggregations = resp.responses().size() > 2 && resp.responses().get(2).isResult()
                ? resp.responses().get(2).result().aggregations()
                : Collections.emptyMap();

        // ---- RRF：score(d) = Σ 1/(k + 该文档在第 i 路里的名次) ----
        // 只看名次不看分数，所以 BM25 的 4.14 和 kNN 的 0.82 这种尺度差异
        // 完全不参与计算 —— 这正是 RRF 想解决的问题。
        Map<String, RrfEntry> fused = new LinkedHashMap<>();
        accumulateRrf(fused, keywordHits, rrfK);
        accumulateRrf(fused, vectorHits, rrfK);

        List<RrfEntry> ranked = new ArrayList<>(fused.values());
        ranked.sort((a, b) -> Double.compare(b.score, a.score));

        List<Hit<SkuEsModel>> page = ranked.stream()
                .skip(from).limit(pageSize)
                .map(e -> e.hit)
                .collect(Collectors.toList());

        // 【total 取两者的较大值】关键词那一路的 total 是真实的匹配总数（可能上千），
        // 而融合列表受 window 限制。纯语义命中时关键词 total 是 0，
        // 这时只能用融合列表的大小 —— 分页导航会偏保守，但不会给出点进去是空的页。
        long keywordTotal = totalOf(resp, 0);
        long total = Math.max(keywordTotal, ranked.size());
        log.info("搜索响应: 模式=rrf, 关键词召回={}, 向量召回={}, 融合后={}, total={}",
                keywordHits.size(), vectorHits.size(), ranked.size(), total);

        return buildSearchResult(page, total, aggregations, param);
    }

    /** 融合中的一条：文档本身 + 累计的 RRF 得分。 */
    private static final class RrfEntry {
        private final Hit<SkuEsModel> hit;
        private double score;

        private RrfEntry(Hit<SkuEsModel> hit) {
            this.hit = hit;
        }
    }

    /**
     * 把一路的排名累加进融合表。
     *
     * <p><b>同一个文档已存在时不替换 hit</b>：关键词那一路先加，它带高亮，
     * 而向量那一路没有。替换掉的话，明明关键词命中了，页面上却不显示高亮。
     */
    private void accumulateRrf(Map<String, RrfEntry> fused, List<Hit<SkuEsModel>> hits, int k) {
        for (int i = 0; i < hits.size(); i++) {
            Hit<SkuEsModel> hit = hits.get(i);
            if (hit.source() == null) {
                continue;
            }
            RrfEntry entry = fused.computeIfAbsent(hit.id(), id -> new RrfEntry(hit));
            entry.score += 1.0d / (k + i + 1);
        }
    }

    private List<Hit<SkuEsModel>> itemHits(MsearchResponse<SkuEsModel> resp, int index) {
        if (resp.responses().size() <= index || !resp.responses().get(index).isResult()) {
            // 某一路失败时不让整个搜索垮掉：另一路的结果仍然可用，
            // 效果等同于退化成单路检索。
            log.warn("msearch 第 {} 路没有结果，本次融合退化为单路", index);
            return Collections.emptyList();
        }
        return resp.responses().get(index).result().hits().hits();
    }

    private long totalOf(MsearchResponse<SkuEsModel> resp, int index) {
        if (resp.responses().size() <= index || !resp.responses().get(index).isResult()) {
            return 0L;
        }
        TotalHits t = resp.responses().get(index).result().hits().total();
        return t != null ? t.value() : 0L;
    }

    // =========================================================================
    // 相似商品推荐
    // =========================================================================

    /** 相似商品最多返回几条，防止调用方传个 1000 把 ES 拖垮。 */
    private static final int MAX_SIMILAR_SIZE = 20;

    /**
     * {@inheritDoc}
     *
     * <h3>为什么是「取回自己的向量」而不是「把标题重新 embed 一遍」</h3>
     * 两条路都能得到一个查询向量，但重新 embed 要走 TEI，
     * 于是<b>详情页的读路径就挂上了 embedding 服务的可用性</b>。
     * 而索引里本来就存着这个 sku 的 titleVector（{@code _source} 没有排除它，
     * 实测 14941/14941 全覆盖），直接取回来既快又不依赖外部服务，
     * 而且和建索引时用的是同一个向量，不会因为模型版本漂移而对不上。
     *
     * <h3>两条必须排除的东西</h3>
     * <ul>
     *   <li><b>自身</b>：最近邻里第一个永远是自己，不排就浪费一个坑位。</li>
     *   <li><b>同 spuId 的其它 sku</b>：那是同一个商品的颜色/版本变体，
     *       标题几乎一样，向量也几乎一样，不排的话整个推荐位会被自己的
     *       规格变体占满 —— 看着"相似度很高"，其实毫无信息量。</li>
     * </ul>
     * 再用 {@code collapse} 按 spuId 折叠，保证每个商品只出一条，
     * 否则别的商品的多个规格也会刷屏。
     *
     * <h3>降级阶梯</h3>
     * 语义召回失败不等于推荐位必须空着。按这个顺序退：
     * <ol>
     *   <li>有向量 → kNN 最近邻</li>
     *   <li>没向量（新商品还没回填）或 kNN 失败 → 同类目按 hotScore 降序</li>
     *   <li>ES 整个不可用 → 空列表，详情页照常渲染</li>
     * </ol>
     */
    @Override
    public List<SkuEsModel> similar(Long skuId, int size) {
        if (skuId == null) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(size, MAX_SIMILAR_SIZE));

        SkuEsModel self;
        try {
            self = fetchSelf(skuId);
        } catch (Exception e) {
            log.warn("相似商品：取不到 skuId={} 的文档，推荐位留空", skuId, e);
            return List.of();
        }
        if (self == null) {
            log.debug("相似商品：skuId={} 不在索引里", skuId);
            return List.of();
        }

        List<Float> vector = self.getTitleVector();
        if (!CollectionUtils.isEmpty(vector)) {
            try {
                return knnSimilar(self, vector, limit);
            } catch (Exception e) {
                log.warn("相似商品：skuId={} 的 kNN 失败，退回同类目热度", skuId, e);
            }
        } else {
            log.debug("相似商品：skuId={} 没有向量，退回同类目热度", skuId);
        }

        try {
            return hotInSameCategory(self, limit);
        } catch (Exception e) {
            log.warn("相似商品：skuId={} 的兜底查询也失败，推荐位留空", skuId, e);
            return List.of();
        }
    }

    /** 取当前 sku 自己的文档，只要推荐需要的几个字段（这里<b>要</b>带上向量）。 */
    private SkuEsModel fetchSelf(Long skuId) throws IOException {
        SearchResponse<SkuEsModel> resp = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .size(1)
                        .query(q -> q.term(t -> t.field("skuId").value(skuId)))
                        .source(sc -> sc.filter(f -> f.includes(
                                List.of("skuId", "spuId", "categoryId", VECTOR_FIELD)))),
                SkuEsModel.class);
        List<Hit<SkuEsModel>> hits = resp.hits().hits();
        return hits.isEmpty() ? null : hits.get(0).source();
    }

    private List<SkuEsModel> knnSimilar(SkuEsModel self, List<Float> vector, int limit) throws IOException {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(q -> q.term(t -> t.field("hasStock").value(true))));
        if (self.getSpuId() != null) {
            // must_not 同 spuId，顺带也就排除了自身（自身必然同 spuId）
            filters.add(Query.of(q -> q.bool(b -> b.mustNot(mn -> mn
                    .term(t -> t.field("spuId").value(String.valueOf(self.getSpuId())))))));
        } else {
            filters.add(Query.of(q -> q.bool(b -> b.mustNot(mn -> mn
                    .term(t -> t.field("skuId").value(self.getSkuId()))))));
        }

        // 【k 要留折叠的余量】collapse 是在 knn 返回的 k 条里折叠的，
        // 不是折叠后再去取更多。k 等于 limit 的话，一旦邻居里有同 spu 的多个规格，
        // 折叠完就不够 limit 条了 —— 表现成「推荐位有时候只有 3 个」。
        int k = Math.min(MAX_KNN_K, limit * 5);
        int numCandidates = Math.min(MAX_KNN_CANDIDATES, Math.max(100, k * 5));

        SearchResponse<SkuEsModel> resp = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .size(limit)
                        .knn(KnnQuery.of(kn -> kn
                                .field(VECTOR_FIELD)
                                .queryVector(vector)
                                .k(k)
                                .numCandidates(numCandidates)
                                .filter(filters)))
                        .collapse(c -> c.field("spuId"))
                        .source(sc -> sc.filter(f -> f.includes(SOURCE_FIELDS))),
                SkuEsModel.class);
        return toModels(resp);
    }

    /**
     * 兜底：同类目里按热度取。
     * <p>这不是"降级版的推荐"，而是<b>另一种推荐</b>——它不需要向量，
     * 所以向量链路整个挂掉时推荐位依然有内容，用户感知不到差别。
     */
    private List<SkuEsModel> hotInSameCategory(SkuEsModel self, int limit) throws IOException {
        if (self.getCategoryId() == null) {
            return List.of();
        }
        SearchResponse<SkuEsModel> resp = esClient.search(s -> s
                        .index(INDEX_NAME)
                        .size(limit)
                        .query(q -> q.bool(b -> {
                            b.filter(f -> f.term(t -> t.field("categoryId").value(self.getCategoryId())));
                            b.filter(f -> f.term(t -> t.field("hasStock").value(true)));
                            if (self.getSpuId() != null) {
                                b.mustNot(mn -> mn.term(t -> t.field("spuId")
                                        .value(String.valueOf(self.getSpuId()))));
                            }
                            return b;
                        }))
                        .sort(so -> so.field(f -> f.field("hotScore").order(SortOrder.Desc)))
                        .collapse(c -> c.field("spuId"))
                        .source(sc -> sc.filter(f -> f.includes(SOURCE_FIELDS))),
                SkuEsModel.class);
        return toModels(resp);
    }

    private List<SkuEsModel> toModels(SearchResponse<SkuEsModel> resp) {
        List<SkuEsModel> out = new ArrayList<>();
        for (Hit<SkuEsModel> hit : resp.hits().hits()) {
            if (hit.source() != null) {
                out.add(hit.source());
            }
        }
        return out;
    }

    /**
     * 左侧筛选面板的三组聚合（品牌 / 分类 / 规格）。
     *
     * <p>抽成 Map 是为了让 score-sum 的单请求和 RRF 的第三路请求共用同一份定义 ——
     * 两边各写一遍的话，改了一处忘了另一处，表现是「切换融合模式后筛选面板少了一栏」。
     */
    private Map<String, Aggregation> buildAggregations() {
        return Map.of(
                "brand_agg", Aggregation.of(a -> a.terms(t -> t.field("brandId"))
                        .aggregations("brand_name_agg", sub -> sub.terms(ts -> ts.field("brandName.keyword")))
                        .aggregations("brand_img_agg", sub -> sub.terms(ts -> ts.field("brandImg")))),
                "category_agg", Aggregation.of(a -> a.terms(t -> t.field("categoryId"))
                        .aggregations("category_name_agg", sub -> sub.terms(ts -> ts.field("categoryName.keyword")))),
                "attr_agg", Aggregation.of(agg -> agg.nested(n -> n.path("attrs"))
                        .aggregations("attr_id_agg", sub -> sub.terms(ts -> ts.field("attrs.attrId"))
                                .aggregations("attr_name_agg", sub2 -> sub2.terms(ts -> ts.field("attrs.attrName")))
                                .aggregations("attr_value_agg", sub3 -> sub3.terms(ts -> ts.field("attrs.attrValue")))))
        );
    }

    /** ES 的 knn 要 {@code List<Float>}，而 TEI 给的是 {@code float[]}。 */
    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }

    /**
     * 构建筛选条件。<b>单独抽出来是因为它有三个使用方</b>：
     * bool 查询、knn 的 filter，以及 RRF 模式下的三路请求。
     * 任何一路漏掉，都会表现成「筛选了但没筛干净」，而且只在特定检索路径上出现。
     */
    private List<Query> buildFilters(SearchParam param) {
        List<Query> filters = new ArrayList<>();

        if (param.getCategoryId() != null) {
            filters.add(Query.of(f -> f.term(t -> t.field("categoryId").value(param.getCategoryId()))));
        }

        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            List<FieldValue> brandValues = param.getBrandId().stream()
                    .filter(Objects::nonNull)
                    .map(FieldValue::of)
                    .collect(Collectors.toList());
            if (!brandValues.isEmpty()) {
                filters.add(Query.of(f -> f.terms(t -> t.field("brandId").terms(new TermsQueryField.Builder().value(brandValues).build()))));
            }
        }

        if (param.getHasStock() != null) {
            boolean hasStock = param.getHasStock() == 1;
            filters.add(Query.of(f -> f.term(t -> t.field("hasStock").value(hasStock))));
        }

        if (StringUtils.hasText(param.getSkuPrice())) {
            RangeQuery.Builder range = new RangeQuery.Builder().field("skuPrice");
            String[] prices = param.getSkuPrice().split("_");
            if (prices.length == 2) {
                if (StringUtils.hasText(prices[0])) {
                    range.gte(co.elastic.clients.json.JsonData.of(toNumber(prices[0])));
                }
                if (StringUtils.hasText(prices[1])) {
                    range.lte(co.elastic.clients.json.JsonData.of(toNumber(prices[1])));
                }
            } else if (param.getSkuPrice().startsWith("_")) {
                String max = param.getSkuPrice().substring(1);
                range.lte(co.elastic.clients.json.JsonData.of(toNumber(max)));
            } else if (param.getSkuPrice().endsWith("_")) {
                String min = param.getSkuPrice().substring(0, param.getSkuPrice().length() - 1);
                range.gte(co.elastic.clients.json.JsonData.of(toNumber(min)));
            }
            filters.add(Query.of(f -> f.range(range.build())));
        }

        if (!CollectionUtils.isEmpty(param.getAttr())) {
            for (String attrStr : param.getAttr()) {
                if (!StringUtils.hasText(attrStr)) {
                    continue;
                }
                String[] split = attrStr.split("_", 2);
                if (split.length != 2) {
                    continue;
                }
                String attrId = split[0];
                String attrValue = split[1];
                filters.add(Query.of(f -> f.nested(n -> n
                        .path("attrs")
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t.field("attrs.attrId").value(attrId)))
                                .must(m -> m.term(t -> t.field("attrs.attrValue").value(attrValue)))
                        ))
                )));
            }
        }

        return filters;
    }

    private SearchRequest buildSearchRequest(SearchParam param, float[] queryVector) {
        int pageSize = param.resolvePageSize(DEFAULT_PAGE_SIZE);
        int pageNum = param.resolvePageNum();
        int from = (pageNum - 1) * pageSize;

        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(INDEX_NAME);

        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (param.hasKeyword()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .fields("skuTitle", "brandName", "categoryName")
                    .query(param.getKeyword())
            ));
        }

        // 【筛选条件单独收集，不直接挂到 bool 上】
        // 因为向量检索（下面的 knn）有它自己的 filter 参数，必须喂【同一批】条件。
        // 只加在 bool 上的话，用户选了「厨房电器」分类，关键词那一路会守规矩，
        // 而向量那一路照样召回全品类的商品 —— 表现为「筛选了但没筛干净」，
        // 而且只在语义命中时才出现，很难复现。
        List<Query> filters = buildFilters(param);

        bool.filter(filters);
        builder.query(q -> q.bool(bool.build()));

        // =====================================================================
        // 语义检索：把查询词变成向量，和上面的关键词检索一起交给 ES
        // =====================================================================
        // 【为什么是同一个请求，而不是查两次再自己合并】
        // ES 8.x 的 top-level knn 和 query 会在同一次检索里合并打分，
        // 而且【knn 召回的文档也参与聚合】（实测：纯 BM25 命中 0 条时聚合是空的，
        // 加上 knn 后聚合变成「厨房电器×10」）。自己查两次再合并的话，
        // 左侧的品牌/分类/属性筛选面板就得自己重算一遍，得不偿失。
        //
        // 【只在有关键词时才做】没有关键词就是按分类浏览，没有「查询意图」可以向量化，
        // 白调一次 TEI 还给每个请求加 10ms。
        //
        // 【queryVector 为 null 是正常路径】TEI 挂了/熔断中就退回纯关键词检索，
        // 搜索照常可用，只是「电饭锅」又搜不到「厨房电器」了。
        // 这条降级是静默的，靠 mall.search.embedding.calls 指标才看得见。
        if (queryVector != null) {
            List<Float> vector = new ArrayList<>(queryVector.length);
            for (float v : queryVector) {
                vector.add(v);
            }
            // 【k 必须覆盖到当前页】knn 只返回最近的 k 个，k 小于 from+size 时
            // 翻到后面的页会凭空少结果 —— 而且是「第 1 页正常、第 3 页变少」这种
            // 不容易被发现的少。
            int k = Math.min(from + pageSize, MAX_KNN_K);
            // num_candidates 是 HNSW 实际遍历的候选数，必须 >= k。给足倍数换准确率：
            // 这是近似检索，候选太少会漏掉真正最近的那些。
            int numCandidates = Math.min(MAX_KNN_CANDIDATES, Math.max(100, k * 5));
            builder.knn(kn -> kn
                    .field(VECTOR_FIELD)
                    .queryVector(vector)
                    .k(k)
                    .numCandidates(numCandidates)
                    .filter(filters)
            );
        }

        // =====================================================================
        // 排序
        // =====================================================================
        // 【这里改过，而且不改的话上面的向量检索等于白做】
        // ES 里只要指定了 sort，_score 就不参与排序了。原先的代码在用户没选排序时
        // 一律按 hotScore 降序 —— 那么无论 BM25 还是 kNN 算出多高的相关性，
        // 结果顺序都只看热度，语义检索的效果一点都体现不出来。
        //
        // 改成：用户显式选了排序就听用户的；没选时，
        //   - 有关键词 -> 不设 sort，走 ES 默认的 _score 降序（相关性优先，搜索的标准语义）
        //   - 无关键词 -> 仍按 hotScore（这时是分类浏览，没有相关性可言，保持原行为）
        //
        // 【为什么不是"拿到向量才按相关性排"】那样排序方式会随 TEI 的可用性跳变：
        // TEI 抖一下，用户刷新页面就看到完全不同的商品顺序。
        // 顺序不是最优，比顺序会无缘无故变化要好得多 —— 降级应该让结果变差，
        // 不该让行为变得不可预测。
        if (StringUtils.hasText(param.getSort())) {
            String[] sortInfo = param.getSort().split("_");
            if (sortInfo.length == 2) {
                String field = sortInfo[0];
                SortOrder order = "asc".equalsIgnoreCase(sortInfo[1]) ? SortOrder.Asc : SortOrder.Desc;
                builder.sort(s -> s.field(f -> f.field(field).order(order)));
            }
        } else if (!param.hasKeyword()) {
            builder.sort(s -> s.field(f -> f.field("hotScore").order(SortOrder.Desc)));
        }

        builder.from(from);
        builder.size(pageSize);

        if (param.hasKeyword()) {
            Highlight highlight = new Highlight.Builder()
                    .fields("skuTitle", h -> h.preTags("<span class='keyword'>").postTags("</span>"))
                    .build();
            builder.highlight(highlight);
        }

        builder.source(SourceConfig.of(sc -> sc.filter(f -> f.includes(SOURCE_FIELDS))));

        // 聚合
        builder.aggregations(buildAggregations());

        return builder.build();
    }

    /**
     * 把检索结果装配成页面模型。
     *
     * <p><b>入参刻意不是 {@code SearchResponse}</b>：score-sum 模式只有一个响应，
     * 而 RRF 模式要把三路响应融合之后才能得到最终的命中列表和聚合，
     * 手工拼一个 {@code SearchResponse} 出来既别扭又容易出错。
     * 这里只要它真正用到的三样东西，两种模式就能共用同一套装配逻辑。
     */
    private SearchResult buildSearchResult(List<Hit<SkuEsModel>> hits,
                                           long total,
                                           Map<String, Aggregate> aggregations,
                                           SearchParam param) {
        SearchResult result = new SearchResult();
        int pageSize = param.resolvePageSize(DEFAULT_PAGE_SIZE);

        // 商品列表
        List<SkuEsModel> products = new ArrayList<>();
        for (Hit<SkuEsModel> hit : hits) {
            SkuEsModel source = hit.source();
            if (source == null) {
                continue;
            }
            if (hit.highlight() != null && hit.highlight().containsKey("skuTitle")) {
                List<String> highlight = hit.highlight().get("skuTitle");
                if (!CollectionUtils.isEmpty(highlight)) {
                    source.setSkuTitle(highlight.get(0));
                }
            }
            products.add(source);
        }
        result.setProducts(products);

        int totalPages = (int) Math.ceil((double) total / pageSize);

        result.setTotal(total);
        result.setTotalPages(totalPages);
        result.setPageNum(param.resolvePageNum());
        result.setPageNavs(buildPageNav(result.getPageNum(), totalPages));

        if (aggregations != null) {
            parseBrandAgg(result, aggregations.get("brand_agg"));
            parseCategoryAgg(result, aggregations.get("category_agg"));
            parseAttrAgg(result, aggregations.get("attr_agg"));
        }

        buildNavs(result, param);
        return result;
    }

    private void parseBrandAgg(SearchResult result, Aggregate agg) {
        if (agg == null) {
            return;
        }
        Aggregate.Kind kind = agg._kind();
        List<SearchResult.BrandVo> brands = new ArrayList<>();
        if (kind == Aggregate.Kind.Sterms) {
            agg.sterms().buckets().array().forEach(bucket ->
                    brands.add(buildBrandVo(bucket.key().stringValue(), bucket)));
        } else if (kind == Aggregate.Kind.Lterms) {
            agg.lterms().buckets().array().forEach(bucket ->
                    brands.add(buildBrandVo(String.valueOf(bucket.key()), bucket)));
        } else {
            return;
        }
        result.setBrands(brands);
    }

    private SearchResult.BrandVo buildBrandVo(String idStr, LongTermsBucket bucket) {
        SearchResult.BrandVo vo = new SearchResult.BrandVo();
        vo.setBrandId(parseLong(idStr));
        vo.setBrandName(getBucketFirstKey(bucket.aggregations().get("brand_name_agg")));
        vo.setBrandImg(getBucketFirstKey(bucket.aggregations().get("brand_img_agg")));
        return vo;
    }

    private SearchResult.BrandVo buildBrandVo(String idStr, StringTermsBucket bucket) {
        SearchResult.BrandVo vo = new SearchResult.BrandVo();
        vo.setBrandId(parseLong(idStr));
        vo.setBrandName(getBucketFirstKey(bucket.aggregations().get("brand_name_agg")));
        vo.setBrandImg(getBucketFirstKey(bucket.aggregations().get("brand_img_agg")));
        return vo;
    }

    private void parseCategoryAgg(SearchResult result, Aggregate agg) {
        if (agg == null) {
            return;
        }
        Aggregate.Kind kind = agg._kind();
        List<SearchResult.CatalogVo> catalogs = new ArrayList<>();
        if (kind == Aggregate.Kind.Sterms) {
            agg.sterms().buckets().array().forEach(bucket ->
                    catalogs.add(buildCatalogVo(bucket.key().stringValue(), bucket)));
        } else if (kind == Aggregate.Kind.Lterms) {
            agg.lterms().buckets().array().forEach(bucket ->
                    catalogs.add(buildCatalogVo(String.valueOf(bucket.key()), bucket)));
        } else {
            return;
        }
        result.setCategories(catalogs);
    }

    private SearchResult.CatalogVo buildCatalogVo(String idStr, LongTermsBucket bucket) {
        SearchResult.CatalogVo vo = new SearchResult.CatalogVo();
        vo.setCategoryId(parseLong(idStr));
        vo.setCategoryName(getBucketFirstKey(bucket.aggregations().get("category_name_agg")));
        return vo;
    }

    private SearchResult.CatalogVo buildCatalogVo(String idStr, StringTermsBucket bucket) {
        SearchResult.CatalogVo vo = new SearchResult.CatalogVo();
        vo.setCategoryId(parseLong(idStr));
        vo.setCategoryName(getBucketFirstKey(bucket.aggregations().get("category_name_agg")));
        return vo;
    }

    private void parseAttrAgg(SearchResult result, Aggregate agg) {
        if (agg == null || agg.nested() == null) {
            return;
        }
        NestedAggregate nestedAggregate = agg.nested();
        Aggregate attrIdAgg = nestedAggregate.aggregations().get("attr_id_agg");
        if (attrIdAgg == null) {
            return;
        }

        List<SearchResult.AttrVo> attrs = new ArrayList<>();
        Aggregate.Kind kind = attrIdAgg._kind();

        if (kind == Aggregate.Kind.Sterms) {
            for (StringTermsBucket bucket : attrIdAgg.sterms().buckets().array()) {
                attrs.add(buildAttrVo(bucket.key().stringValue(), bucket.aggregations()));
            }
        } else if (kind == Aggregate.Kind.Lterms) {
            for (LongTermsBucket bucket : attrIdAgg.lterms().buckets().array()) {
                attrs.add(buildAttrVo(String.valueOf(bucket.key()), bucket.aggregations()));
            }
        }
        result.setAttrs(attrs);
    }

    private SearchResult.AttrVo buildAttrVo(String attrIdStr, Map<String, Aggregate> subAggs) {
        SearchResult.AttrVo vo = new SearchResult.AttrVo();
        vo.setAttrId(parseLong(attrIdStr));
        vo.setAttrName(getBucketFirstKey(subAggs.get("attr_name_agg")));
        Aggregate attrValueAgg = subAggs.get("attr_value_agg");
        if (attrValueAgg != null && attrValueAgg.sterms() != null) {
            List<SearchResult.AttrValueVo> values = attrValueAgg.sterms().buckets().array().stream()
                    .map(b -> {
                        SearchResult.AttrValueVo valueVo = new SearchResult.AttrValueVo();
                        valueVo.setVal(b.key().stringValue());
                        return valueVo;
                    })
                    .collect(Collectors.toList());
            vo.setAttrValue(values);
        } else {
            vo.setAttrValue(Collections.emptyList());
        }
        return vo;
    }
    private void buildNavs(SearchResult result, SearchParam param) {
        List<SearchResult.NavVo> navs = new ArrayList<>();

        if (param.hasKeyword()) {
            SearchResult.NavVo nav = new SearchResult.NavVo();
            nav.setName("关键字");
            nav.setValue(param.getKeyword());
            nav.setLink(buildRemoveLink(param, "keyword", null));
            navs.add(nav);
        }

        if (!CollectionUtils.isEmpty(param.getBrandId()) && !CollectionUtils.isEmpty(result.getBrands())) {
            Map<Long, String> brandNameMap = result.getBrands().stream()
                    .collect(Collectors.toMap(SearchResult.BrandVo::getBrandId, SearchResult.BrandVo::getBrandName, (a, b) -> a));
            for (Long brandId : param.getBrandId()) {
                SearchResult.NavVo nav = new SearchResult.NavVo();
                nav.setName("品牌");
                nav.setValue(brandNameMap.getOrDefault(brandId, String.valueOf(brandId)));
                nav.setLink(buildRemoveLink(param, "brandId", String.valueOf(brandId)));
                navs.add(nav);
            }
        }

        if (!CollectionUtils.isEmpty(param.getAttr())) {
            Map<Long, String> attrNameMap = result.getAttrs().stream()
                    .collect(Collectors.toMap(SearchResult.AttrVo::getAttrId, SearchResult.AttrVo::getAttrName, (a, b) -> a));
            for (String attrStr : param.getAttr()) {
                String[] split = attrStr.split("_", 2);
                if (split.length != 2) {
                    continue;
                }
                Long attrId = parseLong(split[0]);
                String value = split[1];
                SearchResult.NavVo nav = new SearchResult.NavVo();
                nav.setName(attrNameMap.getOrDefault(attrId, "属性"));
                nav.setValue(value);
                nav.setLink(buildRemoveLink(param, "attr", attrStr));
                navs.add(nav);
            }
        }

        result.setNavs(navs);
    }

    private List<Integer> buildPageNav(int pageNum, int totalPages) {
        List<Integer> navs = new ArrayList<>();
        if (totalPages <= 0) {
            return navs;
        }
        int start = Math.max(1, pageNum - PAGE_NAV_SIZE / 2);
        int end = Math.min(totalPages, start + PAGE_NAV_SIZE - 1);
        for (int i = start; i <= end; i++) {
            navs.add(i);
        }
        return navs;
    }

    private String buildRemoveLink(SearchParam param, String key, String value) {
        StringBuilder sb = new StringBuilder("/list.html?");
        if (param.hasKeyword() && !"keyword".equals(key)) {
            sb.append("keyword=").append(param.getKeyword()).append("&");
        }
        if (param.getCategoryId() != null && !"categoryId".equals(key)) {
            sb.append("categoryId=").append(param.getCategoryId()).append("&");
        }
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            for (Long brandId : param.getBrandId()) {
                if ("brandId".equals(key) && String.valueOf(brandId).equals(value)) {
                    continue;
                }
                sb.append("brandId=").append(brandId).append("&");
            }
        }
        if (!CollectionUtils.isEmpty(param.getAttr())) {
            for (String attr : param.getAttr()) {
                if ("attr".equals(key) && attr.equals(value)) {
                    continue;
                }
                sb.append("attr=").append(attr).append("&");
            }
        }
        if (StringUtils.hasText(param.getSkuPrice()) && !"skuPrice".equals(key)) {
            sb.append("skuPrice=").append(param.getSkuPrice()).append("&");
        }
        if (param.getHasStock() != null && !"hasStock".equals(key)) {
            sb.append("hasStock=").append(param.getHasStock()).append("&");
        }
        if (StringUtils.hasText(param.getSort()) && !"sort".equals(key)) {
            sb.append("sort=").append(param.getSort()).append("&");
        }
        if (param.getPageNum() != null && !"pageNum".equals(key)) {
            sb.append("pageNum=").append(param.getPageNum()).append("&");
        }
        String query = sb.toString();
        if (query.endsWith("&") || query.endsWith("?")) {
            query = query.substring(0, query.length() - 1);
        }
        return query;
    }

    private Long parseLong(Number value) {
        return value == null ? null : value.longValue();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getBucketFirstKey(Aggregate aggregate) {
        if (aggregate == null || aggregate.sterms() == null || aggregate.sterms().buckets().array().isEmpty()) {
            return "";
        }
        return Optional.ofNullable(aggregate.sterms().buckets().array().get(0).key())
                .map(k -> k.stringValue())
                .orElse("");
    }

    private java.math.BigDecimal toNumber(String value) {
        try {
            return new java.math.BigDecimal(value);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

}


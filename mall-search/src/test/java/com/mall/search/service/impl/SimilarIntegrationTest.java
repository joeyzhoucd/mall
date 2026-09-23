package com.mall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.mall.search.vo.SkuEsModel;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 相似商品推荐的集成验证。<b>只需要 ES，不需要 TEI</b> ——
 * 这本身就是被测的一个设计点：相似商品用的是索引里已经存着的 titleVector，
 * 详情页的读路径<b>刻意不挂在 embedding 服务上</b>。
 * 如果哪天有人把它改成"把标题重新 embed 一遍"，这个测试会因为
 * 连不上 TEI 而失败，而不是悄悄多出一个依赖。
 *
 * <h3>为什么这些断言是这么设计的</h3>
 * kNN 对<b>任何</b>输入都会返回 k 个最近邻，所以「有结果」什么都证明不了 ——
 * 这个项目在语义搜索上已经栽过一次（中文编码坏成 ????，kNN 照样返回 16 条，
 * 演练全绿）。因此这里不看「有没有结果」，只看三类东西：
 * <ul>
 *   <li><b>硬不变量</b>：不含自身、不含同 spuId。写错 filter 就会挂。</li>
 *   <li><b>质量下界</b>：同类目占比要显著高于随机（全库 27 个类目，随机约 3.7%）。</li>
 *   <li><b>负控</b>：不同输入必须给出不同结果。
 *       「永远返回同一批热门商品」能通过上面所有断言，只有这条能抓到。</li>
 * </ul>
 *
 * <p>跑法：
 * <pre>
 *   kubectl -n mall port-forward svc/elasticsearch 19200:9200 &amp;
 *   mvn -pl mall-search test -Dtest=SimilarIntegrationTest
 * </pre>
 */
class SimilarIntegrationTest {

    private static final String ES_URL = System.getProperty("es.url", "http://127.0.0.1:19200");
    private static final int SIZE = 8;

    private static boolean reachable;

    @BeforeAll
    static void checkEnvironment() {
        try {
            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create(ES_URL)).timeout(Duration.ofSeconds(3)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            reachable = r.statusCode() == 200;
        } catch (Exception e) {
            reachable = false;
        }
    }

    private ElasticsearchClient esClient() {
        RestClient restClient = RestClient.builder(HttpHost.create(ES_URL))
                .setDefaultHeaders(new org.apache.http.Header[]{
                        new BasicHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8"),
                        new BasicHeader("Content-Type", "application/vnd.elasticsearch+json; compatible-with=8")})
                .build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    private SearchServiceImpl service(ElasticsearchClient es) {
        SearchServiceImpl svc = new SearchServiceImpl();
        ReflectionTestUtils.setField(svc, "esClient", es);
        // embeddingClient / fusionProperties 刻意不设：相似商品这条路径用不到它们，
        // 用到了就会 NPE —— 这是故意的，等于把「不许依赖 embedding」写成断言。
        return svc;
    }

    /** 取若干个分属不同类目的商品，作为测试输入。 */
    private List<SkuEsModel> pickAcrossCategories(ElasticsearchClient es, int n) throws Exception {
        SearchResponse<SkuEsModel> resp = es.search(s -> s
                        .index("product")
                        .size(100)
                        .query(q -> q.exists(e -> e.field("titleVector")))
                        .source(sc -> sc.filter(f -> f.includes(
                                List.of("skuId", "spuId", "categoryId", "categoryName")))),
                SkuEsModel.class);
        List<SkuEsModel> picked = new ArrayList<>();
        Set<Long> seenCats = new java.util.HashSet<>();
        for (Hit<SkuEsModel> h : resp.hits().hits()) {
            SkuEsModel m = h.source();
            if (m == null || m.getCategoryId() == null) {
                continue;
            }
            if (seenCats.add(m.getCategoryId())) {
                picked.add(m);
            }
            if (picked.size() >= n) {
                break;
            }
        }
        return picked;
    }

    @Test
    @DisplayName("硬不变量：推荐结果不含自身、不含同一个 spuId 的其它规格")
    void neverRecommendsSelfOrOwnVariants() throws Exception {
        assumeTrue(reachable, "够不到 ES，跳过（本地需要 port-forward）");
        ElasticsearchClient es = esClient();
        SearchServiceImpl svc = service(es);

        List<SkuEsModel> samples = pickAcrossCategories(es, 5);
        assertThat(samples).as("样本取不到就说明索引是空的，这时候下面的断言全是空过").isNotEmpty();

        for (SkuEsModel self : samples) {
            List<SkuEsModel> got = svc.similar(self.getSkuId(), SIZE);
            assertThat(got).as("skuId=%s 应该有推荐结果", self.getSkuId()).isNotEmpty();
            assertThat(got).extracting(SkuEsModel::getSkuId)
                    .as("推荐结果里不能有自己")
                    .doesNotContain(self.getSkuId());
            // similar() 返回的字段里不含 spuId（SOURCE_FIELDS 没有它），
            // 所以这里反查一次：这些 skuId 对应的 spuId 都不能等于自己的
            List<Long> ids = got.stream().map(SkuEsModel::getSkuId).collect(Collectors.toList());
            Set<Long> spuIds = spuIdsOf(es, ids);
            assertThat(spuIds)
                    .as("推荐结果里不能有同一个 spuId 的其它规格")
                    .doesNotContain(self.getSpuId());
        }
    }

    private Set<Long> spuIdsOf(ElasticsearchClient es, List<Long> skuIds) throws Exception {
        SearchResponse<SkuEsModel> resp = es.search(s -> s
                        .index("product")
                        .size(skuIds.size())
                        .query(q -> q.terms(t -> t.field("skuId").terms(v -> v.value(
                                skuIds.stream()
                                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                                        .collect(Collectors.toList())))))
                        .source(sc -> sc.filter(f -> f.includes(List.of("skuId", "spuId")))),
                SkuEsModel.class);
        return resp.hits().hits().stream()
                .map(Hit::source)
                .filter(java.util.Objects::nonNull)
                .map(SkuEsModel::getSpuId)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("质量下界：同类目占比远高于随机")
    void staysInTheSameCategoryFarMoreThanChance() throws Exception {
        assumeTrue(reachable, "够不到 ES，跳过");
        ElasticsearchClient es = esClient();
        SearchServiceImpl svc = service(es);

        List<SkuEsModel> samples = pickAcrossCategories(es, 5);
        assumeTrue(!samples.isEmpty(), "索引里没有带向量的商品");

        int same = 0, total = 0;
        for (SkuEsModel self : samples) {
            List<SkuEsModel> got = svc.similar(self.getSkuId(), SIZE);
            for (SkuEsModel g : got) {
                total++;
                if (self.getCategoryName() != null && self.getCategoryName().equals(g.getCategoryName())) {
                    same++;
                }
            }
        }
        assertThat(total).isPositive();
        double ratio = (double) same / total;
        // 全库 27 个类目，随机命中同类目约 3.7%。这里要求 >= 50%，
        // 离随机足够远，又不至于因为个别跨类目的合理推荐而误报。
        assertThat(ratio)
                .as("同类目占比 %.1f%%，随机基线约 3.7%%；太低说明向量检索没起作用", ratio * 100)
                .isGreaterThanOrEqualTo(0.5);
    }

    @Test
    @DisplayName("负控：不同商品必须给出不同推荐，不能是一份固定的热门榜")
    void differentInputsGiveDifferentResults() throws Exception {
        assumeTrue(reachable, "够不到 ES，跳过");
        ElasticsearchClient es = esClient();
        SearchServiceImpl svc = service(es);

        List<SkuEsModel> samples = pickAcrossCategories(es, 3);
        assumeTrue(samples.size() >= 2, "需要至少两个不同类目的商品");

        List<Set<Long>> results = new ArrayList<>();
        for (SkuEsModel self : samples) {
            results.add(svc.similar(self.getSkuId(), SIZE).stream()
                    .map(SkuEsModel::getSkuId).collect(Collectors.toSet()));
        }
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                Set<Long> a = new java.util.HashSet<>(results.get(i));
                a.retainAll(results.get(j));
                // 不同类目的商品，推荐结果不该有重叠。有重叠就说明
                // 要么向量没起作用，要么退化成了某种全局排序。
                assertThat(a)
                        .as("第 %d 个和第 %d 个商品的推荐结果重叠了，说明推荐和输入无关", i, j)
                        .isEmpty();
            }
        }
    }

    @Test
    @DisplayName("降级：商品不存在时返回空列表而不是抛异常")
    void missingProductDegradesToEmpty() {
        assumeTrue(reachable, "够不到 ES，跳过");
        SearchServiceImpl svc = service(esClient());
        assertThat(svc.similar(-1L, SIZE)).isEmpty();
        assertThat(svc.similar(null, SIZE)).isEmpty();
    }

    @Test
    @DisplayName("上限保护：调用方传个大数也不会让 ES 返回一大堆")
    void clampsSize() throws Exception {
        assumeTrue(reachable, "够不到 ES，跳过");
        ElasticsearchClient es = esClient();
        SearchServiceImpl svc = service(es);
        List<SkuEsModel> samples = pickAcrossCategories(es, 1);
        assumeTrue(!samples.isEmpty(), "索引里没有带向量的商品");

        assertThat(svc.similar(samples.get(0).getSkuId(), 1000)).hasSizeLessThanOrEqualTo(20);
        assertThat(svc.similar(samples.get(0).getSkuId(), 0)).hasSizeLessThanOrEqualTo(1);
    }
}

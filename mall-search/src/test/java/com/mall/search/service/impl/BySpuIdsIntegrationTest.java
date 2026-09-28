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
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 「搭配购买」按 SPU 补全：对真实 ES 跑（和 SimilarIntegrationTest 一样，-Des.url 指过去，
 * 默认 127.0.0.1:19200 = kubectl port-forward svc/elasticsearch 19200:9200；连不上就跳过）。
 */
class BySpuIdsIntegrationTest {

    private static final String ES_URL = System.getProperty("es.url", "http://127.0.0.1:19200");
    private static boolean reachable;

    @BeforeAll
    static void checkEnvironment() {
        try {
            HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create(ES_URL)).timeout(Duration.ofSeconds(3)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            reachable = r.statusCode() == 200;
        } catch (Exception e) {
            reachable = false;
        }
    }

    private static ElasticsearchClient esClient() {
        RestClient restClient = RestClient.builder(HttpHost.create(ES_URL))
                .setDefaultHeaders(new org.apache.http.Header[]{
                        new BasicHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8"),
                        new BasicHeader("Content-Type", "application/vnd.elasticsearch+json; compatible-with=8")})
                .build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    private static SearchServiceImpl service(ElasticsearchClient es) {
        SearchServiceImpl svc = new SearchServiceImpl();
        ReflectionTestUtils.setField(svc, "esClient", es);
        return svc;
    }

    /** 从索引里取 n 个【有货】SKU 所属的不同 SPU */
    private static List<Long> inStockSpus(ElasticsearchClient es, int n) throws Exception {
        SearchResponse<SkuEsModel> resp = es.search(s -> s.index("product").size(200)
                        .query(q -> q.term(t -> t.field("hasStock").value(true)))
                        .source(sc -> sc.filter(f -> f.includes(List.of("spuId")))),
                SkuEsModel.class);
        Set<Long> out = new LinkedHashSet<>();
        for (Hit<SkuEsModel> h : resp.hits().hits()) {
            if (h.source() != null && h.source().getSpuId() != null) {
                out.add(h.source().getSpuId());
            }
            if (out.size() == n) {
                break;
            }
        }
        return new ArrayList<>(out);
    }

    @Test
    void oneInStockSkuPerSpuInTheCallersOrder() throws Exception {
        assumeTrue(reachable, "ES 不可达：" + ES_URL);
        ElasticsearchClient es = esClient();
        List<Long> spus = inStockSpus(es, 10);
        assertThat(spus).hasSize(10);
        // 打乱入参顺序：要验证的是「按入参排」，不是「碰巧和 ES 一样」
        List<Long> order = new ArrayList<>(spus);
        Collections.shuffle(order, new Random(42));

        List<SkuEsModel> got = service(es).bySpuIds(order);

        assertThat(got).extracting(SkuEsModel::getSpuId).containsExactlyElementsOf(order);
        assertThat(got).allSatisfy(m -> {
            assertThat(m.getSkuId()).isNotNull();
            assertThat(m.getSkuTitle()).isNotBlank();
            assertThat(m.getSkuPrice()).isNotNull();
        });
    }

    @Test
    void unknownSpusAreSimplyAbsent() throws Exception {
        assumeTrue(reachable, "ES 不可达：" + ES_URL);
        ElasticsearchClient es = esClient();
        List<Long> real = inStockSpus(es, 3);
        // 第一版用的是 1、2、3 当「不存在」—— 它们是最早那批种子商品，真的在索引里。
        // 所以这里先断言这几个 id 确实不在，前提不成立就别往下测。
        long u1 = Long.MAX_VALUE - 1, u2 = Long.MAX_VALUE - 2, u3 = Long.MAX_VALUE - 3;
        long present = es.count(c -> c.index("product").query(q -> q.terms(t -> t.field("spuId")
                .terms(v -> v.value(List.of(co.elastic.clients.elasticsearch._types.FieldValue.of(String.valueOf(u1)),
                        co.elastic.clients.elasticsearch._types.FieldValue.of(String.valueOf(u2)),
                        co.elastic.clients.elasticsearch._types.FieldValue.of(String.valueOf(u3)))))))).count();
        assertThat(present).as("选作「不存在」的 id 必须真的不在索引里").isZero();
        List<Long> mixed = List.of(u1, real.get(0), u2, real.get(1), real.get(2), u3);

        assertThat(service(es).bySpuIds(mixed)).extracting(SkuEsModel::getSpuId)
                .containsExactly(real.get(0), real.get(1), real.get(2));
    }

    /**
     * 所有 SKU 都没货的 SPU 不出现。这条是变异测试逼出来的：去掉 hasStock 过滤时，
     * 上面两条都不红 —— 它们挑的 SPU 本来就有货，过滤条件在那里是空转的。
     */
    @Test
    void spuWithNoSkuInStockIsAbsent() throws Exception {
        assumeTrue(reachable, "ES 不可达：" + ES_URL);
        ElasticsearchClient es = esClient();
        Long soldOut = null;
        SearchResponse<SkuEsModel> resp = es.search(s -> s.index("product").size(200)
                        .query(q -> q.term(t -> t.field("hasStock").value(false)))
                        .source(sc -> sc.filter(f -> f.includes(List.of("spuId")))),
                SkuEsModel.class);
        for (Hit<SkuEsModel> h : resp.hits().hits()) {
            Long spu = h.source() == null ? null : h.source().getSpuId();
            if (spu == null) {
                continue;
            }
            long inStock = es.count(c -> c.index("product").query(q -> q.bool(b -> b
                    .filter(f -> f.term(t -> t.field("spuId").value(String.valueOf(spu))))
                    .filter(f -> f.term(t -> t.field("hasStock").value(true)))))).count();
            if (inStock == 0) {
                soldOut = spu;
                break;
            }
        }
        assumeTrue(soldOut != null, "索引里没有「全部 SKU 都没货」的 SPU，这条测不了");
        List<Long> real = inStockSpus(es, 2);

        List<SkuEsModel> got = service(es).bySpuIds(List.of(real.get(0), soldOut, real.get(1)));
        assertThat(got).extracting(SkuEsModel::getSpuId).containsExactly(real.get(0), real.get(1));
    }

    /**
     * 上一条在真实数据上跑不起来（索引里 hasStock 全是 true），所以直接断言请求：
     * 有货过滤、按 SPU 折叠、带回 spuId 这三件事必须都在。不依赖 ES，永远会跑。
     */
    @Test
    void requestFiltersInStockCollapsesBySpuAndReturnsSpuId() {
        String req = SearchServiceImpl.bySpuRequest(List.of(11L, 22L)).toString();
        assertThat(req).contains("\"hasStock\":{\"value\":true}");
        assertThat(req).contains("\"collapse\":{\"field\":\"spuId\"}");
        assertThat(req).contains("\"spuId\":[\"11\",\"22\"]");
        assertThat(req).containsPattern("\"includes\":\\[[^\\]]*\"spuId\"");
    }

    @Test
    void emptyOrNullInputGivesEmptyListWithoutCallingEs() {
        SearchServiceImpl svc = new SearchServiceImpl();   // 没注入 esClient：真去查就 NPE
        assertThat(svc.bySpuIds(List.of())).isEmpty();
        assertThat(svc.bySpuIds(null)).isEmpty();
    }

    @Test
    void esFailureDegradesToEmptyList() {
        // 指向一个不存在的端口：契约是不抛异常
        RestClient rc = RestClient.builder(HttpHost.create("http://127.0.0.1:1")).build();
        SearchServiceImpl svc = service(new ElasticsearchClient(new RestClientTransport(rc, new JacksonJsonpMapper())));
        assertThat(svc.bySpuIds(List.of(1L, 2L))).isEmpty();
    }
}

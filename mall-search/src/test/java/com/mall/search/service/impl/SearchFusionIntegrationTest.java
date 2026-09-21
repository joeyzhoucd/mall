package com.mall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.mall.search.client.EmbeddingClient;
import com.mall.search.config.EmbeddingProperties;
import com.mall.search.config.SearchFusionProperties;
import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 两种融合模式的集成验证。<b>需要真实的 ES 和 TEI</b>，够不到就整体跳过，
 * 所以 CI 里它是 skip 的，本地开着 port-forward 时才真的跑。
 *
 * <h3>为什么非要有这么一个「不在 CI 里跑」的测试</h3>
 * 因为 RRF 那条路径靠单元测试验证不了：它的风险集中在
 * {@code _msearch} 的 Java API 用法（三路请求怎么拼、空 header 合不合法）
 * 和融合后的装配（分页切片、高亮从哪一路取、聚合取第三路），
 * 这些全是「编译通过、一跑就错」的地方。
 * 而 mock 掉 ES 之后，测的就只剩我自己写的 mock 了。
 *
 * <p>跑法：
 * <pre>
 *   kubectl -n mall port-forward svc/elasticsearch 19200:9200 &amp;
 *   kubectl -n mall port-forward svc/embedding 18080:80 &amp;
 *   mvn -pl mall-search test -Dtest=SearchFusionIntegrationTest
 * </pre>
 */
class SearchFusionIntegrationTest {

    private static final String ES_URL = System.getProperty("es.url", "http://127.0.0.1:19200");
    private static final String TEI_URL = System.getProperty("tei.url", "http://127.0.0.1:18080");
    /** 这个词在商品标题里一个字都不出现，只有向量能召回它 */
    private static final String SEMANTIC_WORD = "电饭锅";
    /** 这个词标题里真实存在，关键词就能命中 */
    private static final String LITERAL_WORD = "手机";

    private static boolean reachable;

    @BeforeAll
    static void checkEnvironment() {
        reachable = ping(ES_URL) && ping(TEI_URL + "/health");
    }

    private static boolean ping(String url) {
        try {
            HttpResponse<String> r = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2)).build()
                    .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            return r.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private SearchServiceImpl service(String mode) {
        RestClient restClient = RestClient.builder(HttpHost.create(ES_URL))
                .setDefaultHeaders(new org.apache.http.Header[]{
                        new BasicHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8"),
                        new BasicHeader("Content-Type", "application/vnd.elasticsearch+json; compatible-with=8")})
                .build();
        ElasticsearchClient es = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));

        EmbeddingProperties props = new EmbeddingProperties(true, TEI_URL,
                Duration.ofMillis(500), Duration.ofSeconds(3), null, null, null, null, null);
        org.springframework.web.client.RestClient http = org.springframework.web.client.RestClient.builder()
                .baseUrl(TEI_URL)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults().withTimeouts(props.connectTimeout(), props.readTimeout())))
                .build();
        CircuitBreaker cb = CircuitBreakerRegistry.ofDefaults().circuitBreaker("it-" + System.nanoTime());
        EmbeddingClient embedding = new EmbeddingClient(http, cb, props, new SimpleMeterRegistry());

        SearchServiceImpl svc = new SearchServiceImpl();
        ReflectionTestUtils.setField(svc, "esClient", es);
        ReflectionTestUtils.setField(svc, "embeddingClient", embedding);
        ReflectionTestUtils.setField(svc, "fusionProperties",
                new SearchFusionProperties(mode, 50, 60));
        return svc;
    }

    private SearchParam param(String keyword) {
        SearchParam p = new SearchParam();
        p.setKeyword(keyword);
        return p;
    }

    @Test
    @DisplayName("两种模式都能把「电饭锅」这种纯语义查询搜出结果")
    void bothModesFindSemanticOnlyQuery() throws Exception {
        assumeTrue(reachable, "够不到 ES 或 TEI，跳过（本地需要 port-forward）");

        for (String mode : new String[]{SearchFusionProperties.MODE_SCORE_SUM, SearchFusionProperties.MODE_RRF}) {
            SearchResult r = service(mode).search(param(SEMANTIC_WORD));

            assertThat(r.getProducts())
                    .as("%s 模式没能召回「%s」——纯语义查询是这套东西存在的理由", mode, SEMANTIC_WORD)
                    .isNotEmpty();
            assertThat(r.getTotal()).as("%s 模式 total 为 0，分页会算不出来", mode).isPositive();
            // 【这条最关键】关键词一路命中 0 条时，聚合必须由 knn 那一路补上，
            // 否则页面会出现「有 16 个结果，但左侧筛选面板是空的」。
            // RRF 模式下它靠的是专门的第三路请求。
            assertThat(r.getCategories())
                    .as("%s 模式的分类聚合是空的——筛选面板会消失", mode)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("两种模式对字面词都返回带高亮的结果")
    void bothModesHighlightLiteralMatches() throws Exception {
        assumeTrue(reachable, "够不到 ES 或 TEI，跳过");

        for (String mode : new String[]{SearchFusionProperties.MODE_SCORE_SUM, SearchFusionProperties.MODE_RRF}) {
            SearchResult r = service(mode).search(param(LITERAL_WORD));

            assertThat(r.getProducts()).as("%s 模式搜不到字面词", mode).isNotEmpty();
            // RRF 模式下高亮只存在于第一路（关键词那一路）的 hit 里，
            // 融合时如果用 knn 那一路的 hit 覆盖了它，高亮就没了。
            long highlighted = r.getProducts().stream()
                    .filter(p -> p.getSkuTitle() != null && p.getSkuTitle().contains("<span class='keyword'>"))
                    .count();
            assertThat(highlighted)
                    .as("%s 模式一条高亮都没有——融合时把带高亮的那一路覆盖掉了", mode)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("RRF 模式翻到第 2 页仍有结果，且和第 1 页不重复")
    void rrfPaginationWorks() throws Exception {
        assumeTrue(reachable, "够不到 ES 或 TEI，跳过");

        SearchServiceImpl svc = service(SearchFusionProperties.MODE_RRF);
        SearchParam p1 = param(SEMANTIC_WORD);
        SearchParam p2 = param(SEMANTIC_WORD);
        p2.setPageNum(2);

        SearchResult r1 = svc.search(p1);
        SearchResult r2 = svc.search(p2);

        assertThat(r2.getProducts()).as("第 2 页空了——融合后的分页切片没做对").isNotEmpty();
        var firstIds = r1.getProducts().stream().map(x -> x.getSkuId()).toList();
        var secondIds = r2.getProducts().stream().map(x -> x.getSkuId()).toList();
        assertThat(secondIds)
                .as("第 2 页和第 1 页重复了——skip/limit 的偏移量算错了")
                .doesNotContainAnyElementsOf(firstIds);
    }
}

package com.mall.search.config;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticSearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        Header[] defaultHeaders = new Header[] {
            new BasicHeader("Accept", "application/vnd.elasticsearch+json; compatible-with=8"),
            new BasicHeader("Content-Type", "application/x-ndjson; compatible-with=8")
        };
        return RestClient.builder(
                new HttpHost("192.168.77.102", 9200, "http")
        ).setDefaultHeaders(defaultHeaders).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }
}

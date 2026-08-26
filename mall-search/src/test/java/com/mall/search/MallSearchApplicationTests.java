package com.mall.search;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 脚手架自带的空上下文测试。它要求真实的 MySQL/Redis/RabbitMQ/Consul（以及 mall-search 还要 ES）
 * 全部可达，所以本机跑不了（没有 Docker Desktop）。
 * 打上 integration 标签，默认被 surefire 排除，用 mvn test -Pintegration 才会执行。
 * 待办：改造成 Testcontainers 自带中间件，这样 CI 里能真正跑起来，而不只是被跳过。
 */
@Tag("integration")
@SpringBootTest
class MallSearchApplicationTests {
    @Autowired
    private RestClient client;

    @Test
    void testRestClient() throws Exception {
        Request req = new Request("GET", "/product/_doc/2");
        req.setOptions(RequestOptions.DEFAULT.toBuilder().build());
        Response resp = client.performRequest(req);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resp.getEntity().getContent(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            System.out.println(sb.toString());
        }
    }
}

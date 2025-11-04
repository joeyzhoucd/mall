package com.mall.search;

import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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

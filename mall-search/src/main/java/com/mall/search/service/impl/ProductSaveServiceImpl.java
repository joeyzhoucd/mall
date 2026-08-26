package com.mall.search.service.impl;

import com.mall.search.service.ProductSaveService;
import com.mall.search.vo.SkuEsModel;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ProductSaveServiceImpl implements ProductSaveService {

    @Autowired
    private ElasticsearchClient esClient;
    @Autowired
    private ObjectMapper objectMapper;

    private static final String INDEX_NAME = "product";

    @Override
    public boolean productUp(List<Object> skuEsModels) throws IOException {
        ensureIndexExists();

        List<BulkOperation> ops = new ArrayList<>();
        for (Object obj : skuEsModels) {
            SkuEsModel model = objectMapper.convertValue(obj, SkuEsModel.class);
            IndexOperation<SkuEsModel> indexOp = IndexOperation.of(b -> b
                .index(INDEX_NAME)
                .id(String.valueOf(model.getSkuId()))
                .document(model)
            );
            ops.add(BulkOperation.of(b -> b.index(indexOp)));
        }

        BulkRequest bulkReq = BulkRequest.of(b -> b
            .operations(ops)
            .timeout(new Time.Builder().time("60s").build())
            .refresh(Refresh.False)
        );

        BulkResponse bulk = esClient.bulk(bulkReq);
        boolean hasFailures = bulk.errors();

        if (hasFailures) {
            log.error("商品上架存在失败项，items={}", bulk.items());
        } else {
            log.info("商品上架成功，数量={}", ops.size());
        }

        return hasFailures;
    }

    private void ensureIndexExists() throws IOException {
        boolean exists = esClient.indices().exists(ExistsRequest.of(r -> r.index(INDEX_NAME))).value();
        if (!exists) {
            String mappingJson = "{\n" +
                    "  \"mappings\": {\n" +
                    "    \"properties\": {\n" +
                    "      \"skuId\": { \"type\": \"long\" },\n" +
                    "      \"spuId\": { \"type\": \"keyword\" },\n" +
                    "      \"skuTitle\": { \"type\": \"text\", \"analyzer\": \"ik_smart\" },\n" +
                    "      \"skuPrice\": { \"type\": \"scaled_float\", \"scaling_factor\": 100 },\n" +
                    "      \"skuImg\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
                    "      \"saleCount\": { \"type\": \"long\" },\n" +
                    "      \"hasStock\": { \"type\": \"boolean\" },\n" +
                    "      \"hotScore\": { \"type\": \"long\" },\n" +
                    "      \"brandId\": { \"type\": \"long\" },\n" +
                    "      \"categoryId\": { \"type\": \"long\" },\n" +
                    "      \"brandName\": { \"type\": \"text\", \"analyzer\": \"ik_max_word\", \"fields\": { \"keyword\": { \"type\": \"keyword\", \"ignore_above\": 256 } } },\n" +
                    "      \"brandImg\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": true },\n" +
                    "      \"categoryName\": { \"type\": \"text\", \"analyzer\": \"ik_max_word\", \"fields\": { \"keyword\": { \"type\": \"keyword\", \"ignore_above\": 256 } } },\n" +
                    "      \"attrs\": {\n" +
                    "        \"type\": \"nested\",\n" +
                    "        \"properties\": {\n" +
                    "          \"attrId\": { \"type\": \"long\" },\n" +
                    "          \"attrName\": { \"type\": \"keyword\", \"index\": true, \"doc_values\": true },\n" +
                    "          \"attrValue\": { \"type\": \"keyword\" }\n" +
                    "        }\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }\n" +
                    "}";

            esClient.indices().create(new CreateIndexRequest.Builder()
                    .index(INDEX_NAME)
                    .withJson(new StringReader(mappingJson))
                    .build());
            log.info("已创建索引: {}", INDEX_NAME);
        }
    }
}


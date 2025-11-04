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
import com.fasterxml.jackson.databind.ObjectMapper;

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
                    "      \"skuId\": { \"type\": \"keyword\" },\n" +
                    "      \"spuId\": { \"type\": \"keyword\" },\n" +
                    "      \"skuTitle\": { \"type\": \"text\", \"analyzer\": \"ik_smart\" },\n" +
                    "      \"skuPrice\": { \"type\": \"keyword\" },\n" +
                    "      \"skuImg\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
                    "      \"saleCount\": { \"type\": \"long\" },\n" +
                    "      \"hasStock\": { \"type\": \"boolean\" },\n" +
                    "      \"hotScore\": { \"type\": \"long\" },\n" +
                    "      \"brandId\": { \"type\": \"keyword\" },\n" +
                    "      \"categoryId\": { \"type\": \"keyword\" },\n" +
                    "      \"brandName\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
                    "      \"brandImg\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
                    "      \"categoryName\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
                    "      \"attrs\": {\n" +
                    "        \"type\": \"nested\",\n" +
                    "        \"properties\": {\n" +
                    "          \"attrId\": { \"type\": \"keyword\" },\n" +
                    "          \"attrName\": { \"type\": \"keyword\", \"index\": false, \"doc_values\": false },\n" +
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


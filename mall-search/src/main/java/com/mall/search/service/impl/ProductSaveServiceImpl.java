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
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;

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

    /**
     * 批量删除 sku 文档。
     *
     * <h3>「文档不存在」不算失败</h3>
     * ES 的 bulk delete 对不存在的 id 返回 404 状态，但<b>不会</b>把
     * {@code bulk.errors()} 置为 true —— 这正是我们要的：下架一个从没上架过的商品
     * 应该是无害的空操作，而不是报错。调用方（商品删除、下架）可能重试，
     * 必须保证重复执行不会失败。
     */
    @Override
    public boolean productDown(List<Long> skuIds) throws IOException {
        if (skuIds == null || skuIds.isEmpty()) {
            return false;
        }
        // 索引可能还不存在（从没上架过任何商品）。不建的话 bulk 会因为
        // auto-create 而【创建一个没有 mapping 的索引】，之后真正上架时
        // ik 分词、nested attrs 全部失效，搜索结果肉眼看不出错但明显变差。
        ensureIndexExists();

        List<BulkOperation> ops = new ArrayList<>();
        for (Long skuId : skuIds) {
            DeleteOperation deleteOp = DeleteOperation.of(b -> b
                .index(INDEX_NAME)
                .id(String.valueOf(skuId))
            );
            ops.add(BulkOperation.of(b -> b.delete(deleteOp)));
        }

        BulkRequest bulkReq = BulkRequest.of(b -> b
            .operations(ops)
            .timeout(new Time.Builder().time("60s").build())
            .refresh(Refresh.False)
        );

        BulkResponse bulk = esClient.bulk(bulkReq);
        boolean hasFailures = bulk.errors();

        if (hasFailures) {
            log.error("商品下架存在失败项，items={}", bulk.items());
        } else {
            log.info("商品下架成功，数量={}", ops.size());
        }
        return hasFailures;
    }

}


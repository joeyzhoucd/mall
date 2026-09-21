package com.mall.search.service.impl;

import com.mall.search.client.EmbeddingClient;
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
    @Autowired
    private EmbeddingClient embeddingClient;

    private static final String INDEX_NAME = "product";

    @Override
    public boolean productUp(List<Object> skuEsModels) throws IOException {
        ensureIndexExists();

        List<SkuEsModel> models = new ArrayList<>(skuEsModels.size());
        for (Object obj : skuEsModels) {
            models.add(objectMapper.convertValue(obj, SkuEsModel.class));
        }

        fillTitleVectors(models);

        List<BulkOperation> ops = new ArrayList<>();
        for (SkuEsModel model : models) {
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

    /**
     * 给待上架的商品补上标题向量。
     *
     * <h3>这里【不】降级，拿不到向量就让整个上架失败</h3>
     * 和搜索时的处理正好相反。搜索拿不到向量，代价是这一次结果差；
     * 上架跳过向量，代价是<b>这个商品从此再也不出现在语义搜索里</b>，
     * 而且没有任何报错——只有用户搜不到时才会发现，那时早就查不出原因了。
     * 让上架失败，调用方（商品服务的上架流程）会重试或报错给运营，
     * 这比留一条永久性的静默数据缺失好得多。
     *
     * <p>{@code embedAll} 返回 null 是唯一的例外：那表示语义搜索被整体关闭
     * （{@code mall.search.embedding.enabled=false}），此时搜索侧也不用向量，
     * 不生成才是一致的。代价是关闭期间上架的商品需要在重新开启后补灌一次。
     *
     * <p><b>标题为空的商品会被跳过</b>而不是让整批失败：那是数据问题，
     * 不是 TEI 的问题，不该因此挡住同一批里其它正常商品的上架。
     */
    private void fillTitleVectors(List<SkuEsModel> models) {
        List<Integer> positions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < models.size(); i++) {
            String title = models.get(i).getSkuTitle();
            if (title != null && !title.isBlank()) {
                positions.add(i);
                titles.add(title);
            }
        }
        if (titles.isEmpty()) {
            return;
        }

        List<float[]> vectors = embeddingClient.embedAll(titles);
        if (vectors == null) {
            log.info("语义搜索已关闭，本次上架不生成标题向量，数量={}", models.size());
            return;
        }

        for (int i = 0; i < positions.size(); i++) {
            float[] v = vectors.get(i);
            List<Float> boxed = new ArrayList<>(v.length);
            for (float f : v) {
                boxed.add(f);
            }
            models.get(positions.get(i)).setTitleVector(boxed);
        }
        if (positions.size() < models.size()) {
            log.warn("有 {} 个商品没有标题，跳过向量生成（它们仍会上架，但搜不到语义结果）",
                    models.size() - positions.size());
        }
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
                    // 语义检索用的标题向量。
                    //   dims=512    必须和模型输出一致（bge-small-zh-v1.5），写错的文档会被 ES 直接拒绝
                    //   index=true  建 HNSW 近似最近邻索引；false 的话只能全量暴力扫，而且用不了 knn 语法
                    //   dot_product TEI 返回的向量已归一化（实测 ‖v‖=1.0000），
                    //               此时点乘 ≡ 余弦相似度，但省掉每次算模长再相除的开销。
                    //               注意 ES 会校验归一化，不满足会拒绝写入。
                    // 【换模型就要重建索引】维度是建索引时固定的，改不了。
                    "      \"titleVector\": { \"type\": \"dense_vector\", \"dims\": 512, \"index\": true, \"similarity\": \"dot_product\" },\n" +
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


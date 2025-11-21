package com.mall.search.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.NestedAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import com.mall.search.service.SearchService;
import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;
import com.mall.search.vo.SkuEsModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "product";
    private static final int DEFAULT_PAGE_SIZE = 16;
    private static final int PAGE_NAV_SIZE = 5;

    @Autowired
    private ElasticsearchClient esClient;

    @Override
    public SearchResult search(SearchParam param) throws IOException {
        SearchResult result = null;
        try {
            // 1. 构建检索请求
            SearchRequest request = buildSearchRequest(param);
            log.info("搜索请求DSL: {}", request);

            // 2. 执行检索
            SearchResponse<SkuEsModel> response = esClient.search(request, SkuEsModel.class);

            // 3. 分析响应结果
            log.info("搜索响应: 总命中数={}, 耗时={}ms", response.hits().total().value(), response.took());
            result = buildSearchResult(response, param);
        } catch (Exception e) {
            log.error("ES检索异常", e);
            throw e;
        }
        return result;
    }

    private SearchRequest buildSearchRequest(SearchParam param) {
        int pageSize = param.resolvePageSize(DEFAULT_PAGE_SIZE);
        int pageNum = param.resolvePageNum();
        int from = (pageNum - 1) * pageSize;

        SearchRequest.Builder builder = new SearchRequest.Builder();
        builder.index(INDEX_NAME);

        BoolQuery.Builder bool = new BoolQuery.Builder();
        if (param.hasKeyword()) {
            bool.must(m -> m.multiMatch(mm -> mm
                    .fields("skuTitle", "brandName", "categoryName")
                    .query(param.getKeyword())
            ));
        }

        if (param.getCategoryId() != null) {
            bool.filter(f -> f.term(t -> t.field("categoryId").value(param.getCategoryId())));
        }

        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            List<FieldValue> brandValues = param.getBrandId().stream()
                    .filter(Objects::nonNull)
                    .map(FieldValue::of)
                    .collect(Collectors.toList());
            if (!brandValues.isEmpty()) {
                bool.filter(f -> f.terms(t -> t.field("brandId").terms(new TermsQueryField.Builder().value(brandValues).build())));
            }
        }

        if (param.getHasStock() != null) {
            boolean hasStock = param.getHasStock() == 1;
            bool.filter(f -> f.term(t -> t.field("hasStock").value(hasStock)));
        }

        if (StringUtils.hasText(param.getSkuPrice())) {
            RangeQuery.Builder range = new RangeQuery.Builder().field("skuPrice");
            String[] prices = param.getSkuPrice().split("_");
            if (prices.length == 2) {
                if (StringUtils.hasText(prices[0])) {
                    range.gte(co.elastic.clients.json.JsonData.of(toNumber(prices[0])));
                }
                if (StringUtils.hasText(prices[1])) {
                    range.lte(co.elastic.clients.json.JsonData.of(toNumber(prices[1])));
                }
            } else if (param.getSkuPrice().startsWith("_")) {
                String max = param.getSkuPrice().substring(1);
                range.lte(co.elastic.clients.json.JsonData.of(toNumber(max)));
            } else if (param.getSkuPrice().endsWith("_")) {
                String min = param.getSkuPrice().substring(0, param.getSkuPrice().length() - 1);
                range.gte(co.elastic.clients.json.JsonData.of(toNumber(min)));
            }
            bool.filter(f -> f.range(range.build()));
        }

        if (!CollectionUtils.isEmpty(param.getAttr())) {
            for (String attrStr : param.getAttr()) {
                if (!StringUtils.hasText(attrStr)) {
                    continue;
                }
                String[] split = attrStr.split("_", 2);
                if (split.length != 2) {
                    continue;
                }
                String attrId = split[0];
                String attrValue = split[1];
                bool.filter(f -> f.nested(n -> n
                        .path("attrs")
                        .query(q -> q.bool(b -> b
                                .must(m -> m.term(t -> t.field("attrs.attrId").value(attrId)))
                                .must(m -> m.term(t -> t.field("attrs.attrValue").value(attrValue)))
                        ))
                ));
            }
        }

        builder.query(q -> q.bool(bool.build()));

        // sort
        if (StringUtils.hasText(param.getSort())) {
            String[] sortInfo = param.getSort().split("_");
            if (sortInfo.length == 2) {
                String field = sortInfo[0];
                SortOrder order = "asc".equalsIgnoreCase(sortInfo[1]) ? SortOrder.Asc : SortOrder.Desc;
                builder.sort(s -> s.field(f -> f.field(field).order(order)));
            }
        } else {
            builder.sort(s -> s.field(f -> f.field("hotScore").order(SortOrder.Desc)));
        }

        builder.from(from);
        builder.size(pageSize);

        if (param.hasKeyword()) {
            Highlight highlight = new Highlight.Builder()
                    .fields("skuTitle", h -> h.preTags("<span class='keyword'>").postTags("</span>"))
                    .build();
            builder.highlight(highlight);
        }

        builder.source(SourceConfig.of(sc -> sc.filter(f -> f.includes("skuId", "skuTitle", "skuPrice", "skuImg", "saleCount", "brandName", "brandImg", "categoryName"))));

        // 聚合
        builder.aggregations("brand_agg", a -> a.terms(t -> t.field("brandId"))
                .aggregations("brand_name_agg", sub -> sub.terms(ts -> ts.field("brandName.keyword")))
                .aggregations("brand_img_agg", sub -> sub.terms(ts -> ts.field("brandImg")))
        );

        builder.aggregations("category_agg", a -> a.terms(t -> t.field("categoryId"))
                .aggregations("category_name_agg", sub -> sub.terms(ts -> ts.field("categoryName.keyword")))
        );

        builder.aggregations("attr_agg", agg -> agg.nested(n -> n.path("attrs"))
                .aggregations("attr_id_agg", sub -> sub.terms(ts -> ts.field("attrs.attrId"))
                        .aggregations("attr_name_agg", sub2 -> sub2.terms(ts -> ts.field("attrs.attrName")))
                        .aggregations("attr_value_agg", sub3 -> sub3.terms(ts -> ts.field("attrs.attrValue")))
                )
        );

        return builder.build();
    }

    private SearchResult buildSearchResult(SearchResponse<SkuEsModel> response, SearchParam param) {
        SearchResult result = new SearchResult();
        int pageSize = param.resolvePageSize(DEFAULT_PAGE_SIZE);

        // 商品列表
        List<SkuEsModel> products = new ArrayList<>();
        for (Hit<SkuEsModel> hit : response.hits().hits()) {
            SkuEsModel source = hit.source();
            if (source == null) {
                continue;
            }
            if (hit.highlight() != null && hit.highlight().containsKey("skuTitle")) {
                List<String> highlight = hit.highlight().get("skuTitle");
                if (!CollectionUtils.isEmpty(highlight)) {
                    source.setSkuTitle(highlight.get(0));
                }
            }
            products.add(source);
        }
        result.setProducts(products);

        TotalHits totalHits = response.hits().total();
        long total = totalHits != null ? totalHits.value() : 0L;
        int totalPages = (int) Math.ceil((double) total / pageSize);

        result.setTotal(total);
        result.setTotalPages(totalPages);
        result.setPageNum(param.resolvePageNum());
        result.setPageNavs(buildPageNav(result.getPageNum(), totalPages));

        Map<String, Aggregate> aggregations = response.aggregations();
        if (aggregations != null) {
            parseBrandAgg(result, aggregations.get("brand_agg"));
            parseCategoryAgg(result, aggregations.get("category_agg"));
            parseAttrAgg(result, aggregations.get("attr_agg"));
        }

        buildNavs(result, param);
        return result;
    }

    private void parseBrandAgg(SearchResult result, Aggregate agg) {
        if (agg == null) {
            return;
        }
        Aggregate.Kind kind = agg._kind();
        List<SearchResult.BrandVo> brands = new ArrayList<>();
        if (kind == Aggregate.Kind.Sterms) {
            agg.sterms().buckets().array().forEach(bucket ->
                    brands.add(buildBrandVo(bucket.key().stringValue(), bucket)));
        } else if (kind == Aggregate.Kind.Lterms) {
            agg.lterms().buckets().array().forEach(bucket ->
                    brands.add(buildBrandVo(String.valueOf(bucket.key()), bucket)));
        } else {
            return;
        }
        result.setBrands(brands);
    }

    private SearchResult.BrandVo buildBrandVo(String idStr, LongTermsBucket bucket) {
        SearchResult.BrandVo vo = new SearchResult.BrandVo();
        vo.setBrandId(parseLong(idStr));
        vo.setBrandName(getBucketFirstKey(bucket.aggregations().get("brand_name_agg")));
        vo.setBrandImg(getBucketFirstKey(bucket.aggregations().get("brand_img_agg")));
        return vo;
    }

    private SearchResult.BrandVo buildBrandVo(String idStr, StringTermsBucket bucket) {
        SearchResult.BrandVo vo = new SearchResult.BrandVo();
        vo.setBrandId(parseLong(idStr));
        vo.setBrandName(getBucketFirstKey(bucket.aggregations().get("brand_name_agg")));
        vo.setBrandImg(getBucketFirstKey(bucket.aggregations().get("brand_img_agg")));
        return vo;
    }

    private void parseCategoryAgg(SearchResult result, Aggregate agg) {
        if (agg == null) {
            return;
        }
        Aggregate.Kind kind = agg._kind();
        List<SearchResult.CatalogVo> catalogs = new ArrayList<>();
        if (kind == Aggregate.Kind.Sterms) {
            agg.sterms().buckets().array().forEach(bucket ->
                    catalogs.add(buildCatalogVo(bucket.key().stringValue(), bucket)));
        } else if (kind == Aggregate.Kind.Lterms) {
            agg.lterms().buckets().array().forEach(bucket ->
                    catalogs.add(buildCatalogVo(String.valueOf(bucket.key()), bucket)));
        } else {
            return;
        }
        result.setCategories(catalogs);
    }

    private SearchResult.CatalogVo buildCatalogVo(String idStr, LongTermsBucket bucket) {
        SearchResult.CatalogVo vo = new SearchResult.CatalogVo();
        vo.setCategoryId(parseLong(idStr));
        vo.setCategoryName(getBucketFirstKey(bucket.aggregations().get("category_name_agg")));
        return vo;
    }

    private SearchResult.CatalogVo buildCatalogVo(String idStr, StringTermsBucket bucket) {
        SearchResult.CatalogVo vo = new SearchResult.CatalogVo();
        vo.setCategoryId(parseLong(idStr));
        vo.setCategoryName(getBucketFirstKey(bucket.aggregations().get("category_name_agg")));
        return vo;
    }

    private void parseAttrAgg(SearchResult result, Aggregate agg) {
        if (agg == null || agg.nested() == null) {
            return;
        }
        NestedAggregate nestedAggregate = agg.nested();
        Aggregate attrIdAgg = nestedAggregate.aggregations().get("attr_id_agg");
        if (attrIdAgg == null) {
            return;
        }

        List<SearchResult.AttrVo> attrs = new ArrayList<>();
        Aggregate.Kind kind = attrIdAgg._kind();

        if (kind == Aggregate.Kind.Sterms) {
            for (StringTermsBucket bucket : attrIdAgg.sterms().buckets().array()) {
                attrs.add(buildAttrVo(bucket.key().stringValue(), bucket.aggregations()));
            }
        } else if (kind == Aggregate.Kind.Lterms) {
            for (LongTermsBucket bucket : attrIdAgg.lterms().buckets().array()) {
                attrs.add(buildAttrVo(String.valueOf(bucket.key()), bucket.aggregations()));
            }
        }
        result.setAttrs(attrs);
    }

    private SearchResult.AttrVo buildAttrVo(String attrIdStr, Map<String, Aggregate> subAggs) {
        SearchResult.AttrVo vo = new SearchResult.AttrVo();
        vo.setAttrId(parseLong(attrIdStr));
        vo.setAttrName(getBucketFirstKey(subAggs.get("attr_name_agg")));
        Aggregate attrValueAgg = subAggs.get("attr_value_agg");
        if (attrValueAgg != null && attrValueAgg.sterms() != null) {
            List<SearchResult.AttrValueVo> values = attrValueAgg.sterms().buckets().array().stream()
                    .map(b -> {
                        SearchResult.AttrValueVo valueVo = new SearchResult.AttrValueVo();
                        valueVo.setVal(b.key().stringValue());
                        return valueVo;
                    })
                    .collect(Collectors.toList());
            vo.setAttrValue(values);
        } else {
            vo.setAttrValue(Collections.emptyList());
        }
        return vo;
    }
    private void buildNavs(SearchResult result, SearchParam param) {
        List<SearchResult.NavVo> navs = new ArrayList<>();

        if (param.hasKeyword()) {
            SearchResult.NavVo nav = new SearchResult.NavVo();
            nav.setName("关键字");
            nav.setValue(param.getKeyword());
            nav.setLink(buildRemoveLink(param, "keyword", null));
            navs.add(nav);
        }

        if (!CollectionUtils.isEmpty(param.getBrandId()) && !CollectionUtils.isEmpty(result.getBrands())) {
            Map<Long, String> brandNameMap = result.getBrands().stream()
                    .collect(Collectors.toMap(SearchResult.BrandVo::getBrandId, SearchResult.BrandVo::getBrandName, (a, b) -> a));
            for (Long brandId : param.getBrandId()) {
                SearchResult.NavVo nav = new SearchResult.NavVo();
                nav.setName("品牌");
                nav.setValue(brandNameMap.getOrDefault(brandId, String.valueOf(brandId)));
                nav.setLink(buildRemoveLink(param, "brandId", String.valueOf(brandId)));
                navs.add(nav);
            }
        }

        if (!CollectionUtils.isEmpty(param.getAttr())) {
            Map<Long, String> attrNameMap = result.getAttrs().stream()
                    .collect(Collectors.toMap(SearchResult.AttrVo::getAttrId, SearchResult.AttrVo::getAttrName, (a, b) -> a));
            for (String attrStr : param.getAttr()) {
                String[] split = attrStr.split("_", 2);
                if (split.length != 2) {
                    continue;
                }
                Long attrId = parseLong(split[0]);
                String value = split[1];
                SearchResult.NavVo nav = new SearchResult.NavVo();
                nav.setName(attrNameMap.getOrDefault(attrId, "属性"));
                nav.setValue(value);
                nav.setLink(buildRemoveLink(param, "attr", attrStr));
                navs.add(nav);
            }
        }

        result.setNavs(navs);
    }

    private List<Integer> buildPageNav(int pageNum, int totalPages) {
        List<Integer> navs = new ArrayList<>();
        if (totalPages <= 0) {
            return navs;
        }
        int start = Math.max(1, pageNum - PAGE_NAV_SIZE / 2);
        int end = Math.min(totalPages, start + PAGE_NAV_SIZE - 1);
        for (int i = start; i <= end; i++) {
            navs.add(i);
        }
        return navs;
    }

    private String buildRemoveLink(SearchParam param, String key, String value) {
        StringBuilder sb = new StringBuilder("/list.html?");
        if (param.hasKeyword() && !"keyword".equals(key)) {
            sb.append("keyword=").append(param.getKeyword()).append("&");
        }
        if (param.getCategoryId() != null && !"categoryId".equals(key)) {
            sb.append("categoryId=").append(param.getCategoryId()).append("&");
        }
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            for (Long brandId : param.getBrandId()) {
                if ("brandId".equals(key) && String.valueOf(brandId).equals(value)) {
                    continue;
                }
                sb.append("brandId=").append(brandId).append("&");
            }
        }
        if (!CollectionUtils.isEmpty(param.getAttr())) {
            for (String attr : param.getAttr()) {
                if ("attr".equals(key) && attr.equals(value)) {
                    continue;
                }
                sb.append("attr=").append(attr).append("&");
            }
        }
        if (StringUtils.hasText(param.getSkuPrice()) && !"skuPrice".equals(key)) {
            sb.append("skuPrice=").append(param.getSkuPrice()).append("&");
        }
        if (param.getHasStock() != null && !"hasStock".equals(key)) {
            sb.append("hasStock=").append(param.getHasStock()).append("&");
        }
        if (StringUtils.hasText(param.getSort()) && !"sort".equals(key)) {
            sb.append("sort=").append(param.getSort()).append("&");
        }
        if (param.getPageNum() != null && !"pageNum".equals(key)) {
            sb.append("pageNum=").append(param.getPageNum()).append("&");
        }
        String query = sb.toString();
        if (query.endsWith("&") || query.endsWith("?")) {
            query = query.substring(0, query.length() - 1);
        }
        return query;
    }

    private Long parseLong(Number value) {
        return value == null ? null : value.longValue();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getBucketFirstKey(Aggregate aggregate) {
        if (aggregate == null || aggregate.sterms() == null || aggregate.sterms().buckets().array().isEmpty()) {
            return "";
        }
        return Optional.ofNullable(aggregate.sterms().buckets().array().get(0).key())
                .map(k -> k.stringValue())
                .orElse("");
    }

    private java.math.BigDecimal toNumber(String value) {
        try {
            return new java.math.BigDecimal(value);
        } catch (NumberFormatException e) {
            return java.math.BigDecimal.ZERO;
        }
    }

}


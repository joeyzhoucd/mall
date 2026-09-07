package com.mall.product.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkuHotReadCacheTest {

    private static final Path SKU_INFO_SOURCE = Path.of(
            "src/main/java/com/mall/product/service/impl/SkuInfoServiceImpl.java");
    private static final Path CART_FEIGN_SOURCE = Path.of(
            "src/main/java/com/mall/product/controller/CartFeignController.java");
    private static final Path SKU_IMAGES_SOURCE = Path.of(
            "src/main/java/com/mall/product/service/impl/SkuImagesServiceImpl.java");
    private static final Path SKU_SALE_ATTR_SOURCE = Path.of(
            "src/main/java/com/mall/product/service/impl/SkuSaleAttrValueServiceImpl.java");

    @Test
    @DisplayName("sku item detail and price reads use MultiLevelCacheClient")
    void skuItemAndPriceReadsUseMultiLevelCache() throws IOException {
        String source = Files.readString(SKU_INFO_SOURCE, StandardCharsets.UTF_8);

        assertTrue(methodBody(source, "item").contains("ProductHotCacheInvalidator.SKU_ITEM_CACHE_NAME"),
                "item(skuId) must cache the assembled product detail through MultiLevelCacheClient");
        assertTrue(methodBody(source, "getBySkuId").contains("ProductHotCacheInvalidator.SKU_INFO_CACHE_NAME"),
                "getBySkuId(skuId), including price reads, must use the unified multi-level cache");
        assertTrue(methodBody(source, "listSkuIdsBySpuId").contains("ProductHotCacheInvalidator.SPU_SKU_IDS_CACHE_NAME"),
                "spuId -> skuIds reads used by warmup must use the unified multi-level cache");
        assertTrue(source.contains("ProductHotCacheInvalidator.SKU_IMAGES_CACHE_NAME"),
                "sku images used by detail pages must use the unified multi-level cache");
        assertTrue(source.contains("ProductHotCacheInvalidator.SPU_SALE_ATTRS_CACHE_NAME"),
                "spu sale attributes used by detail pages must use the unified multi-level cache");
        assertTrue(source.contains("ProductHotCacheInvalidator.SPU_DESC_CACHE_NAME"),
                "spu descriptions used by detail pages must use the unified multi-level cache");
        assertTrue(source.contains("ProductHotCacheInvalidator.SPU_ATTR_GROUPS_CACHE_NAME"),
                "spu attribute groups used by detail pages must use the unified multi-level cache");
        assertTrue(Files.readString(CART_FEIGN_SOURCE, StandardCharsets.UTF_8)
                        .contains("skuInfoService.listSkuIdsBySpuId(spuId)"),
                "product feign must expose cached spuId -> skuIds lookup for homepage stock warmup");
    }

    @Test
    @DisplayName("cart feign sku info endpoint uses cached sku lookup")
    void cartFeignSkuInfoUsesCachedLookup() throws IOException {
        String source = Files.readString(CART_FEIGN_SOURCE, StandardCharsets.UTF_8);

        assertTrue(source.contains("skuInfoService.getBySkuId(skuId)"),
                "cart feign sku info endpoint must not bypass sku price cache via getById");
    }

    @Test
    @DisplayName("sku writes evict product hot caches after commit")
    void skuWritesEvictHotCachesAfterCommit() throws IOException {
        String source = Files.readString(SKU_INFO_SOURCE, StandardCharsets.UTF_8);

        assertTrue(methodBody(source, "updateById").contains("evictSkuAfterCommit"),
                "sku basic info writes must evict cached sku info and item detail after commit");
        assertTrue(methodBody(source, "removeByIds").contains("evictSkusAfterCommit"),
                "sku deletes must evict cached sku info and item detail after commit");
        assertTrue(methodBody(source, "batchPublish").contains("evictSkusAfterCommit"),
                "batch publish status writes must evict cached sku info and item detail after commit");
    }

    @Test
    @DisplayName("sku child writes evict detail caches")
    void skuChildWritesEvictDetailCaches() throws IOException {
        String images = Files.readString(SKU_IMAGES_SOURCE, StandardCharsets.UTF_8);
        String saleAttrs = Files.readString(SKU_SALE_ATTR_SOURCE, StandardCharsets.UTF_8);

        assertTrue(images.contains("evictSkusAfterCommit"),
                "sku image writes must evict cached image and item detail data after commit");
        assertTrue(saleAttrs.contains("SKU_SALE_ATTR_VALUES_CACHE_NAME"),
                "cart sale attribute reads must use the unified multi-level cache");
        assertTrue(saleAttrs.contains("evictSaleAttrsAfterCommit"),
                "sku sale attribute writes must evict sku and spu-level detail caches after commit");
    }

    private static String methodBody(String source, String methodName) {
        String marker = "public ";
        int nameStart = -1;
        int from = 0;
        while (true) {
            int candidate = source.indexOf(methodName + "(", from);
            assertTrue(candidate >= 0, "source does not contain method: " + methodName);

            int declarationStart = source.lastIndexOf(marker, candidate);
            int openingBrace = source.indexOf("{", declarationStart);
            if (declarationStart >= 0 && openingBrace > candidate) {
                nameStart = candidate;
                break;
            }
            from = candidate + methodName.length();
        }
        int start = source.lastIndexOf(marker, nameStart);

        int next = source.indexOf("\n    public ", start + marker.length());
        if (next < 0) {
            next = source.length();
        }
        return source.substring(start, next);
    }
}

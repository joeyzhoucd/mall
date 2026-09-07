package com.mall.product.cache;

import com.mall.common.cache.MultiLevelCacheClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;

@Component
public class ProductHotCacheInvalidator {

    public static final String SKU_INFO_CACHE_NAME = "product:sku-info";
    public static final String SKU_ITEM_CACHE_NAME = "product:sku-item";
    public static final String SKU_IMAGES_CACHE_NAME = "product:sku-images";
    public static final String SKU_SALE_ATTR_VALUES_CACHE_NAME = "product:sku-sale-attr-values";
    public static final String SPU_DESC_CACHE_NAME = "product:spu-desc";
    public static final String SPU_SALE_ATTRS_CACHE_NAME = "product:spu-sale-attrs";
    public static final String SPU_ATTR_GROUPS_CACHE_NAME = "product:spu-attr-groups";

    private final MultiLevelCacheClient multiLevelCacheClient;

    public ProductHotCacheInvalidator(MultiLevelCacheClient multiLevelCacheClient) {
        this.multiLevelCacheClient = multiLevelCacheClient;
    }

    public static String key(Long id) {
        return String.valueOf(id);
    }

    public void evictSku(Long skuId) {
        if (skuId == null) {
            return;
        }
        String key = key(skuId);
        multiLevelCacheClient.evict(SKU_INFO_CACHE_NAME, key);
        multiLevelCacheClient.evict(SKU_ITEM_CACHE_NAME, key);
        multiLevelCacheClient.evict(SKU_IMAGES_CACHE_NAME, key);
        multiLevelCacheClient.evict(SKU_SALE_ATTR_VALUES_CACHE_NAME, key);
    }

    public void evictSkus(Collection<Long> skuIds) {
        if (CollectionUtils.isEmpty(skuIds)) {
            return;
        }
        skuIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictSku);
    }

    public void evictSpu(Long spuId) {
        if (spuId == null) {
            return;
        }
        String key = key(spuId);
        multiLevelCacheClient.evict(SPU_DESC_CACHE_NAME, key);
        multiLevelCacheClient.evict(SPU_SALE_ATTRS_CACHE_NAME, key);
        multiLevelCacheClient.evict(SPU_ATTR_GROUPS_CACHE_NAME, key);
    }

    public void evictSpus(Collection<Long> spuIds) {
        if (CollectionUtils.isEmpty(spuIds)) {
            return;
        }
        spuIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictSpu);
    }

    public void evictSkuAfterCommit(Long skuId) {
        afterCommit(() -> evictSku(skuId));
    }

    public void evictSkusAfterCommit(Collection<Long> skuIds) {
        afterCommit(() -> evictSkus(skuIds));
    }

    public void evictSpuAfterCommit(Long spuId) {
        afterCommit(() -> evictSpu(spuId));
    }

    public void evictSpusAfterCommit(Collection<Long> spuIds) {
        afterCommit(() -> evictSpus(spuIds));
    }

    public void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}

package com.mall.ware.cache;

import com.mall.common.cache.MultiLevelCacheClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;

@Component
public class WareHotCacheInvalidator {

    public static final String WARE_SKU_BY_SKU_CACHE_NAME = "ware:sku-by-sku";
    public static final String SKU_AVAILABLE_STOCK_CACHE_NAME = "ware:sku-available-stock";

    private final MultiLevelCacheClient multiLevelCacheClient;

    public WareHotCacheInvalidator(MultiLevelCacheClient multiLevelCacheClient) {
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
        multiLevelCacheClient.evict(WARE_SKU_BY_SKU_CACHE_NAME, key);
        multiLevelCacheClient.evict(SKU_AVAILABLE_STOCK_CACHE_NAME, key);
    }

    public void evictSkus(Collection<Long> skuIds) {
        if (CollectionUtils.isEmpty(skuIds)) {
            return;
        }
        skuIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictSku);
    }

    public void evictSkuAfterCommit(Long skuId) {
        afterCommit(() -> evictSku(skuId));
    }

    public void evictSkusAfterCommit(Collection<Long> skuIds) {
        afterCommit(() -> evictSkus(skuIds));
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

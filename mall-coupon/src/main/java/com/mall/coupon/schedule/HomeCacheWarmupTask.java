package com.mall.coupon.schedule;

import com.mall.common.constant.ResponseKeys;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.coupon.entity.HomeSubjectEntity;
import com.mall.coupon.entity.HomeSubjectSpuEntity;
import com.mall.coupon.feign.ProductFeignService;
import com.mall.coupon.feign.WareFeignService;
import com.mall.coupon.service.HomeAdvService;
import com.mall.coupon.service.HomeSubjectService;
import com.mall.coupon.service.HomeSubjectSpuService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "mall.coupon.cache.home.warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HomeCacheWarmupTask {

    private static final Logger log = LoggerFactory.getLogger(HomeCacheWarmupTask.class);
    private static final String LOCK_KEY = "coupon:home-cache:warmup:lock";
    private static final TypeReference<List<Long>> SKU_IDS_TYPE = new TypeReference<>() {
    };

    private final HomeAdvService homeAdvService;
    private final HomeSubjectService homeSubjectService;
    private final HomeSubjectSpuService homeSubjectSpuService;
    private final ProductFeignService productFeignService;
    private final WareFeignService wareFeignService;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    @Value("${mall.coupon.cache.home.warmup.max-subjects:20}")
    private int maxSubjects;

    @Value("${mall.coupon.cache.home.warmup.max-spus:100}")
    private int maxSpus;

    @Value("${mall.coupon.cache.home.warmup.max-skus:300}")
    private int maxSkus;

    public HomeCacheWarmupTask(HomeAdvService homeAdvService,
                               HomeSubjectService homeSubjectService,
                               HomeSubjectSpuService homeSubjectSpuService,
                               ProductFeignService productFeignService,
                               WareFeignService wareFeignService,
                               RedissonClient redissonClient,
                               ObjectMapper objectMapper) {
        this.homeAdvService = homeAdvService;
        this.homeSubjectService = homeSubjectService;
        this.homeSubjectSpuService = homeSubjectSpuService;
        this.productFeignService = productFeignService;
        this.wareFeignService = wareFeignService;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelayString = "${mall.coupon.cache.home.warmup.initial-delay-ms:30000}",
            fixedDelayString = "${mall.coupon.cache.home.warmup.fixed-delay-ms:300000}")
    public void warmup() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            WarmupResult result = warmupOnce();
            log.info("首页缓存预热完成: adv={} subjects={} subjectSpus={} skus={}",
                    result.advCount(), result.subjectCount(), result.subjectSpuCount(), result.skuCount());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("首页缓存预热失败，本轮跳过: {}", e.getMessage());
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    WarmupResult warmupOnce() {
        int advCount = homeAdvService.listActive().size();
        List<HomeSubjectEntity> subjects = limit(homeSubjectService.listActive(), maxSubjects);
        Set<Long> spuIds = new LinkedHashSet<>();
        int subjectSpuCount = 0;

        for (HomeSubjectEntity subject : subjects) {
            if (subject == null || subject.getId() == null || spuIds.size() >= maxSpus) {
                continue;
            }
            List<HomeSubjectSpuEntity> subjectSpus = homeSubjectSpuService.listBySubjectId(subject.getId());
            subjectSpuCount += subjectSpus.size();
            for (HomeSubjectSpuEntity subjectSpu : subjectSpus) {
                if (subjectSpu != null && subjectSpu.getSpuId() != null) {
                    spuIds.add(subjectSpu.getSpuId());
                    if (spuIds.size() >= maxSpus) {
                        break;
                    }
                }
            }
        }

        List<Long> skuIds = resolveSkuIds(spuIds);
        if (!skuIds.isEmpty()) {
            wareFeignService.warmStockCache(skuIds);
        }
        return new WarmupResult(advCount, subjects.size(), subjectSpuCount, skuIds.size());
    }

    private List<Long> resolveSkuIds(Set<Long> spuIds) {
        List<Long> skuIds = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long spuId : spuIds) {
            if (seen.size() >= maxSkus) {
                break;
            }
            List<Long> ids = lookupSkuIds(spuId);
            for (Long skuId : ids) {
                if (skuId != null) {
                    seen.add(skuId);
                    if (seen.size() >= maxSkus) {
                        break;
                    }
                }
            }
        }
        skuIds.addAll(seen);
        return skuIds;
    }

    private List<Long> lookupSkuIds(Long spuId) {
        try {
            R response = productFeignService.getSkuIdsBySpuId(spuId);
            List<Long> skuIds = RUtils.getData(response, ResponseKeys.SKU_IDS, objectMapper, SKU_IDS_TYPE);
            return skuIds == null ? List.of() : skuIds;
        } catch (Exception e) {
            log.warn("spuId={} 查询 SKU 清单失败，跳过库存预热: {}", spuId, e.getMessage());
            return List.of();
        }
    }

    private <T> List<T> limit(List<T> list, int max) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        int size = Math.min(Math.max(max, 0), list.size());
        return list.subList(0, size);
    }

    record WarmupResult(int advCount, int subjectCount, int subjectSpuCount, int skuCount) {
    }
}

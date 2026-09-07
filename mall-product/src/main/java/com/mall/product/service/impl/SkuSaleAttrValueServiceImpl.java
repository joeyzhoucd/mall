package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.cache.ProductHotCacheInvalidator;
import com.mall.product.dao.SkuInfoDao;
import com.mall.product.dao.SkuSaleAttrValueDao;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.entity.SkuSaleAttrValueEntity;
import com.mall.product.service.SkuSaleAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service("skuSaleAttrValueService")
public class SkuSaleAttrValueServiceImpl extends ServiceImpl<SkuSaleAttrValueDao, SkuSaleAttrValueEntity> implements SkuSaleAttrValueService {

    private static final TypeReference<List<String>> SKU_SALE_ATTR_VALUES_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions SKU_SALE_ATTR_VALUES_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30),
            Duration.ofMinutes(10),
            Duration.ofSeconds(30),
            true,
            Duration.ofSeconds(5),
            Duration.ofMillis(300),
            Duration.ofMillis(20),
            0.1);

    @Autowired
    private MultiLevelCacheClient multiLevelCacheClient;

    @Autowired
    private ProductHotCacheInvalidator productHotCacheInvalidator;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuSaleAttrValueEntity> page = this.page(
                new Query<SkuSaleAttrValueEntity>().getPage(params),
                new QueryWrapper<SkuSaleAttrValueEntity>().orderByDesc("id")
        );

        return new PageUtils(page);
    }

    @Override
    public List<String> getSkuSaleAttrValuesAsStringList(Long skuId) {
        if (skuId == null) {
            return List.of();
        }
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SKU_SALE_ATTR_VALUES_CACHE_NAME,
                ProductHotCacheInvalidator.key(skuId),
                SKU_SALE_ATTR_VALUES_TYPE,
                () -> this.baseMapper.getSkuSaleAttrValuesAsStringList(skuId),
                SKU_SALE_ATTR_VALUES_CACHE_OPTIONS);
    }

    @Override
    public boolean save(SkuSaleAttrValueEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            evictSaleAttrsAfterCommit(Arrays.asList(entity.getSkuId()));
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<SkuSaleAttrValueEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            evictSaleAttrsAfterCommit(skuIdsFromEntities(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(SkuSaleAttrValueEntity entity) {
        Long oldSkuId = null;
        if (entity != null && entity.getId() != null) {
            SkuSaleAttrValueEntity old = getById(entity.getId());
            oldSkuId = old == null ? null : old.getSkuId();
        }
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            evictSaleAttrsAfterCommit(Arrays.asList(oldSkuId, entity.getSkuId()));
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> ids = normalizeIds(list);
        if (ids.isEmpty()) {
            return false;
        }
        List<Long> skuIds = listByIds(ids).stream()
                .map(SkuSaleAttrValueEntity::getSkuId)
                .collect(Collectors.toList());
        boolean result = super.removeByIds(list);
        if (result) {
            evictSaleAttrsAfterCommit(skuIds);
        }
        return result;
    }

    private void evictSaleAttrsAfterCommit(Collection<Long> skuIds) {
        List<SkuInfoEntity> skuInfos = skuInfos(skuIds);
        List<Long> spuIds = skuInfos.stream().map(SkuInfoEntity::getSpuId).collect(Collectors.toList());
        List<Long> affectedSkuIds = skuInfosBySpuIds(spuIds).stream()
                .map(SkuInfoEntity::getSkuId)
                .collect(Collectors.toList());
        if (affectedSkuIds.isEmpty()) {
            affectedSkuIds = normalizeIds(skuIds);
        }
        productHotCacheInvalidator.evictSkusAfterCommit(affectedSkuIds);
        productHotCacheInvalidator.evictSpusAfterCommit(spuIds);
    }

    private List<SkuInfoEntity> skuInfos(Collection<Long> skuIds) {
        List<Long> ids = normalizeIds(skuIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return skuInfoDao.selectBatchIds(ids);
    }

    private List<SkuInfoEntity> skuInfosBySpuIds(Collection<Long> spuIds) {
        List<Long> ids = normalizeIds(spuIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return skuInfoDao.selectList(new QueryWrapper<SkuInfoEntity>().in("spu_id", ids));
    }

    private List<Long> normalizeIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(id -> Long.valueOf(String.valueOf(id)))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> skuIdsFromEntities(Collection<SkuSaleAttrValueEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(SkuSaleAttrValueEntity::getSkuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }
}

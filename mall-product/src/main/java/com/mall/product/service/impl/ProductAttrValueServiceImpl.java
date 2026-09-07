package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.cache.ProductHotCacheInvalidator;
import com.mall.product.dao.ProductAttrValueDao;
import com.mall.product.dao.SkuInfoDao;
import com.mall.product.entity.ProductAttrValueEntity;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.service.ProductAttrValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Service("productAttrValueService")
public class ProductAttrValueServiceImpl extends ServiceImpl<ProductAttrValueDao, ProductAttrValueEntity> implements ProductAttrValueService {

    @Autowired
    private ProductHotCacheInvalidator productHotCacheInvalidator;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<ProductAttrValueEntity> page = this.page(
                new Query<ProductAttrValueEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public boolean save(ProductAttrValueEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            evictSpuAttrsAfterCommit(Arrays.asList(entity.getSpuId()));
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<ProductAttrValueEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            evictSpuAttrsAfterCommit(spuIdsFromEntities(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(ProductAttrValueEntity entity) {
        Long oldSpuId = null;
        if (entity != null && entity.getId() != null) {
            ProductAttrValueEntity old = getById(entity.getId());
            oldSpuId = old == null ? null : old.getSpuId();
        }
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            evictSpuAttrsAfterCommit(Arrays.asList(oldSpuId, entity.getSpuId()));
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> ids = normalizeIds(list);
        if (ids.isEmpty()) {
            return false;
        }
        List<Long> spuIds = listByIds(ids).stream()
                .map(ProductAttrValueEntity::getSpuId)
                .collect(Collectors.toList());
        boolean result = super.removeByIds(list);
        if (result) {
            evictSpuAttrsAfterCommit(spuIds);
        }
        return result;
    }

    private void evictSpuAttrsAfterCommit(Collection<Long> spuIds) {
        List<Long> ids = normalizeIds(spuIds);
        productHotCacheInvalidator.evictSpusAfterCommit(ids);
        productHotCacheInvalidator.evictSkusAfterCommit(skuIdsBySpuIds(ids));
    }

    private List<Long> skuIdsBySpuIds(Collection<Long> spuIds) {
        List<Long> ids = normalizeIds(spuIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        return skuInfoDao.selectList(new QueryWrapper<SkuInfoEntity>().in("spu_id", ids)).stream()
                .map(SkuInfoEntity::getSkuId)
                .collect(Collectors.toList());
    }

    private List<Long> spuIdsFromEntities(Collection<ProductAttrValueEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(ProductAttrValueEntity::getSpuId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
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
}

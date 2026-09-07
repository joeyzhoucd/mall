package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.cache.ProductHotCacheInvalidator;
import com.mall.product.dao.SkuInfoDao;
import com.mall.product.dao.SpuInfoDescDao;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.entity.SpuInfoDescEntity;
import com.mall.product.service.SpuInfoDescService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service("spuInfoDescService")
public class SpuInfoDescServiceImpl extends ServiceImpl<SpuInfoDescDao, SpuInfoDescEntity> implements SpuInfoDescService {

    @Autowired
    private ProductHotCacheInvalidator productHotCacheInvalidator;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoDescEntity> page = this.page(
                new Query<SpuInfoDescEntity>().getPage(params),
                new QueryWrapper<SpuInfoDescEntity>().orderByDesc("spu_id")
        );

        return new PageUtils(page);
    }

    @Override
    public boolean save(SpuInfoDescEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            evictSpuDescAfterCommit(Collections.singletonList(entity.getSpuId()));
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<SpuInfoDescEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            evictSpuDescAfterCommit(spuIdsFromEntities(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(SpuInfoDescEntity entity) {
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            evictSpuDescAfterCommit(Collections.singletonList(entity.getSpuId()));
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> spuIds = normalizeIds(list);
        if (spuIds.isEmpty()) {
            return false;
        }
        boolean result = super.removeByIds(list);
        if (result) {
            evictSpuDescAfterCommit(spuIds);
        }
        return result;
    }

    private void evictSpuDescAfterCommit(Collection<Long> spuIds) {
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

    private List<Long> spuIdsFromEntities(Collection<SpuInfoDescEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(SpuInfoDescEntity::getSpuId)
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

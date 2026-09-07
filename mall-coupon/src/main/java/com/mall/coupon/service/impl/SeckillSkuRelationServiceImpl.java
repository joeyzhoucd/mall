package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.coupon.cache.PromotionHotCacheInvalidator;
import com.mall.coupon.dao.SeckillSkuRelationDao;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.SeckillSkuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service("seckillSkuRelationService")
public class SeckillSkuRelationServiceImpl extends ServiceImpl<SeckillSkuRelationDao, SeckillSkuRelationEntity> implements SeckillSkuRelationService {

    private static final TypeReference<SeckillSkuRelationEntity> SECKILL_RELATION_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions SECKILL_RELATION_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(10),
            Duration.ofMinutes(2),
            Duration.ofSeconds(10),
            true,
            Duration.ofSeconds(3),
            Duration.ofMillis(200),
            Duration.ofMillis(20),
            0.1);

    @Autowired
    private MultiLevelCacheClient multiLevelCacheClient;

    @Autowired
    private PromotionHotCacheInvalidator promotionHotCacheInvalidator;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SeckillSkuRelationEntity> page = this.page(
                new Query<SeckillSkuRelationEntity>().getPage(params),
                new QueryWrapper<SeckillSkuRelationEntity>().orderByDesc("id")
        );

        return new PageUtils(page);
    }

    @Override
    public SeckillSkuRelationEntity getById(Serializable id) {
        Long relationId = normalizeId(id);
        if (relationId == null) {
            return super.getById(id);
        }
        return multiLevelCacheClient.get(PromotionHotCacheInvalidator.SECKILL_RELATION_CACHE_NAME,
                PromotionHotCacheInvalidator.key(relationId),
                SECKILL_RELATION_TYPE,
                () -> baseMapper.selectById(relationId),
                SECKILL_RELATION_CACHE_OPTIONS);
    }

    @Override
    public void incrementSoldCount(Long relationId) {
        this.update(new UpdateWrapper<SeckillSkuRelationEntity>()
                .eq("id", relationId)
                .setSql("sold_count = sold_count + 1"));
        promotionHotCacheInvalidator.evictRelationAfterCommit(relationId);
    }

    @Override
    public boolean save(SeckillSkuRelationEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            promotionHotCacheInvalidator.evictRelationAfterCommit(entity.getId());
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<SeckillSkuRelationEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            promotionHotCacheInvalidator.evictRelationsAfterCommit(relationIds(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(SeckillSkuRelationEntity entity) {
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            promotionHotCacheInvalidator.evictRelationAfterCommit(entity.getId());
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> relationIds = normalizeIds(list);
        if (relationIds.isEmpty()) {
            return false;
        }
        boolean result = super.removeByIds(list);
        if (result) {
            promotionHotCacheInvalidator.evictRelationsAfterCommit(relationIds);
        }
        return result;
    }

    private List<Long> relationIds(Collection<SeckillSkuRelationEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(SeckillSkuRelationEntity::getId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Long> normalizeIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .map(this::normalizeId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Long normalizeId(Object id) {
        if (id == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(id));
    }

}

package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.coupon.cache.PromotionHotCacheInvalidator;
import com.mall.coupon.dao.SeckillSessionDao;
import com.mall.coupon.entity.SeckillSessionEntity;
import com.mall.coupon.service.SeckillSessionService;
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


@Service("seckillSessionService")
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionDao, SeckillSessionEntity> implements SeckillSessionService {

    private static final TypeReference<SeckillSessionEntity> SECKILL_SESSION_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions SECKILL_SESSION_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
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
        IPage<SeckillSessionEntity> page = this.page(
                new Query<SeckillSessionEntity>().getPage(params),
                new QueryWrapper<SeckillSessionEntity>().orderByDesc("id")
        );

        return new PageUtils(page);
    }

    @Override
    public SeckillSessionEntity getById(Serializable id) {
        Long sessionId = normalizeId(id);
        if (sessionId == null) {
            return super.getById(id);
        }
        return multiLevelCacheClient.get(PromotionHotCacheInvalidator.SECKILL_SESSION_CACHE_NAME,
                PromotionHotCacheInvalidator.key(sessionId),
                SECKILL_SESSION_TYPE,
                () -> baseMapper.selectById(sessionId),
                SECKILL_SESSION_CACHE_OPTIONS);
    }

    @Override
    public boolean save(SeckillSessionEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            promotionHotCacheInvalidator.evictSessionAfterCommit(entity.getId());
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<SeckillSessionEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            promotionHotCacheInvalidator.evictSessionsAfterCommit(sessionIds(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(SeckillSessionEntity entity) {
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            promotionHotCacheInvalidator.evictSessionAfterCommit(entity.getId());
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> sessionIds = normalizeIds(list);
        if (sessionIds.isEmpty()) {
            return false;
        }
        boolean result = super.removeByIds(list);
        if (result) {
            promotionHotCacheInvalidator.evictSessionsAfterCommit(sessionIds);
        }
        return result;
    }

    private List<Long> sessionIds(Collection<SeckillSessionEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(SeckillSessionEntity::getId)
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

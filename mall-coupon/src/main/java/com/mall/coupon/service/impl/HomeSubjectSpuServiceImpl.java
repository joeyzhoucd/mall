package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.coupon.cache.PromotionHotCacheInvalidator;
import com.mall.coupon.dao.HomeSubjectSpuDao;
import com.mall.coupon.entity.HomeSubjectSpuEntity;
import com.mall.coupon.service.HomeSubjectSpuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@Service("homeSubjectSpuService")
public class HomeSubjectSpuServiceImpl extends ServiceImpl<HomeSubjectSpuDao, HomeSubjectSpuEntity> implements HomeSubjectSpuService {

    private static final TypeReference<List<HomeSubjectSpuEntity>> HOME_SUBJECT_SPU_LIST_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions HOME_SUBJECT_SPU_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30),
            Duration.ofMinutes(10),
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
        IPage<HomeSubjectSpuEntity> page = this.page(
                new Query<HomeSubjectSpuEntity>().getPage(params),
                new QueryWrapper<HomeSubjectSpuEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<HomeSubjectSpuEntity> listBySubjectId(Long subjectId) {
        if (subjectId == null) {
            return List.of();
        }
        return multiLevelCacheClient.get(PromotionHotCacheInvalidator.HOME_SUBJECT_SPU_CACHE_NAME,
                PromotionHotCacheInvalidator.key(subjectId),
                HOME_SUBJECT_SPU_LIST_TYPE,
                () -> baseMapper.selectList(new QueryWrapper<HomeSubjectSpuEntity>()
                        .eq("subject_id", subjectId)
                        .orderByAsc("sort")
                        .orderByDesc("id")),
                HOME_SUBJECT_SPU_CACHE_OPTIONS);
    }

    @Override
    public boolean save(HomeSubjectSpuEntity entity) {
        boolean result = super.save(entity);
        if (result && entity != null) {
            promotionHotCacheInvalidator.evictHomeSubjectSpuAfterCommit(entity.getSubjectId());
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<HomeSubjectSpuEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectSpusAfterCommit(subjectIds(entityList));
        }
        return result;
    }

    @Override
    public boolean updateById(HomeSubjectSpuEntity entity) {
        Long oldSubjectId = null;
        if (entity != null && entity.getId() != null) {
            HomeSubjectSpuEntity old = super.getById(entity.getId());
            oldSubjectId = old == null ? null : old.getSubjectId();
        }
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            List<Long> subjectIds = java.util.stream.Stream.of(oldSubjectId, entity.getSubjectId())
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            promotionHotCacheInvalidator.evictHomeSubjectSpusAfterCommit(subjectIds);
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> ids = normalizeIds(list);
        if (ids.isEmpty()) {
            return false;
        }
        List<Long> subjectIds = listByIds(ids).stream()
                .map(HomeSubjectSpuEntity::getSubjectId)
                .collect(Collectors.toList());
        boolean result = super.removeByIds(list);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectSpusAfterCommit(subjectIds);
        }
        return result;
    }

    private List<Long> subjectIds(Collection<HomeSubjectSpuEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        return entities.stream()
                .map(HomeSubjectSpuEntity::getSubjectId)
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

package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.coupon.cache.PromotionHotCacheInvalidator;
import com.mall.coupon.dao.HomeSubjectDao;
import com.mall.coupon.entity.HomeSubjectEntity;
import com.mall.coupon.service.HomeSubjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;


@Service("homeSubjectService")
public class HomeSubjectServiceImpl extends ServiceImpl<HomeSubjectDao, HomeSubjectEntity> implements HomeSubjectService {

    private static final TypeReference<List<HomeSubjectEntity>> HOME_SUBJECT_LIST_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions HOME_SUBJECT_CACHE_OPTIONS = new MultiLevelCacheOptions(
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
        IPage<HomeSubjectEntity> page = this.page(
                new Query<HomeSubjectEntity>().getPage(params),
                new QueryWrapper<HomeSubjectEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<HomeSubjectEntity> listActive() {
        return multiLevelCacheClient.get(PromotionHotCacheInvalidator.HOME_SUBJECT_ACTIVE_CACHE_NAME,
                PromotionHotCacheInvalidator.HOME_ACTIVE_CACHE_KEY,
                HOME_SUBJECT_LIST_TYPE,
                this::loadActiveFromDb,
                HOME_SUBJECT_CACHE_OPTIONS);
    }

    @Override
    public boolean save(HomeSubjectEntity entity) {
        boolean result = super.save(entity);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectsAfterCommit();
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<HomeSubjectEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectsAfterCommit();
        }
        return result;
    }

    @Override
    public boolean updateById(HomeSubjectEntity entity) {
        boolean result = super.updateById(entity);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectsAfterCommit();
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        boolean result = super.removeByIds(list);
        if (result) {
            promotionHotCacheInvalidator.evictHomeSubjectsAfterCommit();
        }
        return result;
    }

    private List<HomeSubjectEntity> loadActiveFromDb() {
        return baseMapper.selectList(new QueryWrapper<HomeSubjectEntity>()
                .eq("status", 1)
                .orderByAsc("sort")
                .orderByDesc("id"));
    }
}

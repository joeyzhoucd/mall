package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.coupon.cache.PromotionHotCacheInvalidator;
import com.mall.coupon.dao.HomeAdvDao;
import com.mall.coupon.entity.HomeAdvEntity;
import com.mall.coupon.service.HomeAdvService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;


@Service("homeAdvService")
public class HomeAdvServiceImpl extends ServiceImpl<HomeAdvDao, HomeAdvEntity> implements HomeAdvService {

    private static final TypeReference<List<HomeAdvEntity>> HOME_ADV_LIST_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions HOME_ADV_CACHE_OPTIONS = new MultiLevelCacheOptions(
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
        IPage<HomeAdvEntity> page = this.page(
                new Query<HomeAdvEntity>().getPage(params),
                new QueryWrapper<HomeAdvEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<HomeAdvEntity> listActive() {
        return multiLevelCacheClient.get(PromotionHotCacheInvalidator.HOME_ADV_ACTIVE_CACHE_NAME,
                PromotionHotCacheInvalidator.HOME_ACTIVE_CACHE_KEY,
                HOME_ADV_LIST_TYPE,
                this::loadActiveFromDb,
                HOME_ADV_CACHE_OPTIONS);
    }

    @Override
    public boolean save(HomeAdvEntity entity) {
        boolean result = super.save(entity);
        if (result) {
            promotionHotCacheInvalidator.evictHomeAdvAfterCommit();
        }
        return result;
    }

    @Override
    public boolean saveBatch(Collection<HomeAdvEntity> entityList) {
        boolean result = super.saveBatch(entityList);
        if (result) {
            promotionHotCacheInvalidator.evictHomeAdvAfterCommit();
        }
        return result;
    }

    @Override
    public boolean updateById(HomeAdvEntity entity) {
        boolean result = super.updateById(entity);
        if (result) {
            promotionHotCacheInvalidator.evictHomeAdvAfterCommit();
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        boolean result = super.removeByIds(list);
        if (result) {
            promotionHotCacheInvalidator.evictHomeAdvAfterCommit();
        }
        return result;
    }

    private List<HomeAdvEntity> loadActiveFromDb() {
        Date now = new Date();
        return baseMapper.selectList(new QueryWrapper<HomeAdvEntity>()
                .eq("status", 1)
                .and(w -> w.isNull("start_time").or().le("start_time", now))
                .and(w -> w.isNull("end_time").or().ge("end_time", now))
                .orderByAsc("sort")
                .orderByDesc("id"));
    }
}

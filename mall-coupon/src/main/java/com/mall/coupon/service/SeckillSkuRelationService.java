package com.mall.coupon.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.coupon.entity.SeckillSkuRelationEntity;

import java.util.Map;


public interface SeckillSkuRelationService extends IService<SeckillSkuRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 已售数量原子自增，供消费者建单成功后回调用——用数据库的原子 UPDATE
     * （sold_count = sold_count + 1）而不是"查出来改字段再存回去"，避免并发覆盖。
     */
    void incrementSoldCount(Long relationId);
}

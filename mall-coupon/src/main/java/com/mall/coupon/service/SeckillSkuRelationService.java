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

    /**
     * 直接读库，绕开 {@code getById} 的多级缓存（本地 10s / Redis 2m）。
     *
     * <p>激活要用这个：激活把 seckillCount / seckillPrice 固化进 {@code seckill:stock} / {@code seckill:info}，
     * 之后抢购和下单价只认那两个 Redis key，而同一场活动不允许再激活一次。读到缓存里的旧行
     * （后台刚改完价格或库存、失效消息还没到）就会让整场活动按旧价旧量卖完，事后改不回来。
     * 展示类的读取继续走 {@code getById}。
     */
    SeckillSkuRelationEntity getByIdFromDb(Long relationId);
}

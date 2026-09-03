package com.mall.coupon.service;

import com.mall.coupon.vo.SeckillSchedulerVo;

/**
 * 后台「一键给某个 SKU 配置秒杀」。
 *
 * <h3>为什么需要它，而不是让前端调两个现成的 CRUD</h3>
 * 秒杀在数据模型上是两层：场次 {@code sms_seckill_session}（时间段）+
 * 关系 {@code sms_seckill_sku_relation}（某场次里的某个 SKU 卖多少、什么价）。
 * 而后台 SKU 页想要的是一个动作：「这个商品，这个时间段，这个价，这么多件」。
 * <p>
 * 让前端自己编排这两次写入有三个问题：两次写入不在一个事务里
 * （第二次失败会留下一个空场次）；「同样的时间段是否复用已有场次」这条规则
 * 会散落在前端；而前端也拿不到判断「这个活动是不是正在进行中」所需要的 Redis 状态。
 * 所以这一层放在服务端。
 */
public interface SeckillSchedulerService {

    /**
     * 配置（或更新）一个 SKU 的秒杀。
     *
     * @return 这条秒杀关系的 id —— 调用方拿它去
     *         {@code POST /coupon/seckill/activate/{relationId}} 激活。
     *         <b>本方法刻意不自动激活</b>，理由见实现类。
     * @throws IllegalArgumentException 参数不合法，或试图修改一个已激活活动的库存
     */
    Long save(SeckillSchedulerVo vo);
}

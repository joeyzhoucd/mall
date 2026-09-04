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

    /**
     * 后台激活一场秒杀（把库存放进 Redis，正式开卖）。
     *
     * <h3>为什么后台需要一个自己的入口，而不是直接调
     * {@code POST /coupon/seckill/activate/{relationId}}</h3>
     * 那个入口要求 {@code X-Seckill-Internal-Token} 头。把内部令牌发到浏览器里
     * 等于公开它 —— 任何拿到它的人都能开卖任意一场秒杀。
     * 所以后台走这条路：它挂在 {@code /api/**} 下，由网关的 AdminAuthFilter
     * 校验管理端 JWT，鉴权在网关那一层完成，令牌不出服务端。
     *
     * <h3>已激活时必须拒绝，不能重复激活</h3>
     * {@code SeckillGrabService.activate()} 做的是<b>重置</b>：
     * 库存计数写回 {@code seckillCount}，并且<b>删掉</b> {@code seckill:user:{id}}
     * 那个每人限购的记录键。所以对一场<b>正在进行</b>的秒杀再点一次「激活」：
     * <ol>
     *   <li>库存回满 —— 已经卖出去的不算了</li>
     *   <li>限购记录清空 —— <b>所有已经抢中的人都能再抢一次</b></li>
     * </ol>
     * 超卖立刻发生，而且界面上只是「点了一下按钮」。
     * 所以这里以 Redis 里的库存键为准判断是否已上线，已上线就拒绝。
     * <p>
     * 需要故意重置（比如压测前重来一轮）的话，走那个带内部令牌的入口 ——
     * 那是运维动作，不该做成后台上一个可以误点的按钮。
     *
     * @throws IllegalArgumentException 关系不存在
     * @throws IllegalStateException    已经激活过了
     */
    void activate(Long relationId);
}

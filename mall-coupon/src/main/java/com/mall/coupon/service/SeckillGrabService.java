package com.mall.coupon.service;

import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.vo.SeckillGrabResultVo;

public interface SeckillGrabService {

    /**
     * 上架/激活秒杀：把这个 relation 的库存数预热进 Redis，同时清空上次遗留的抢购用户名单
     * （如果是重新激活同一场）。真正的库存来源仍然是 sms_seckill_sku_relation.seckill_count，
     * 这里只是把它复制一份到 Redis 供高并发抢购用的原子扣减，不会去动 wms_ware_sku 的实际库存
     * ——也就是说这版实现没有在上架时把库存从普通渠道里"划出来"，普通购物车/正常下单和秒杀
     * 抢到的这批库存在数据库层面仍然共享同一个池子，秒杀赢家最终建单锁库存时如果被普通购物
     * 提前买光会失败（这个失败走的是异常场景，不是秒杀设计要解决的核心并发问题）。
     */
    boolean activate(Long relationId);

    /**
     * 秒杀抢购：Redis 原子网关挡住全部并发，赢了的人（数量天然被库存上限限流）才会往下走
     * 查地址、写本地消息表。如果查到默认地址，直接在这一步把 MQ 也发出去（下单异步进行，
     * 前端轮询订单号即可）；如果没有默认地址，先只落本地消息表（addr_id 留空），
     * 等前端确认页选完地址后调用 {@link #submitAddress} 才真正发 MQ。
     * <p>
     * 赢了 Redis 网关之后的所有步骤都在同一个 try 里：任何异常都会先把 Redis 那份
     * "库存-1、用户已抢"状态回滚掉再对外报错，避免库存名额凭空消失。
     * 如果这个用户上次抢购曾经因为发 MQ 失败而卡在 SEND_FAILED，这次会复用同一条
     * 本地消息记录重新尝试，而不是被当成"已经抢过"直接拒绝。
     */
    SeckillGrabResultVo grab(Long relationId, Long memberId, String username);

    /**
     * 确认页选完地址后回填并发 MQ 建单。只有本人能操作自己抢到的那条本地消息记录
     * （用 memberId 做归属校验），且只在状态还是"待发送"时才真正发送，避免重复提交重发 MQ。
     * username 现在从 message 自己身上读（createPending 时已经落库），不用调用方再传——
     * 这样对账任务补发消息时不需要伪造一个登录会话。
     */
    SeckillGrabResultVo submitAddress(Long messageId, Long memberId, Long addrId);

    /**
     * mall-order 消费 MQ 建单成功后回调，回填 order_sn 并给这场秒杀的已售数量 +1。
     * 按 message 当前状态做幂等判断——已经是"已建单"就直接跳过，防止 MQ 重投或
     * mall-order 那边重试回调导致 sold_count 被重复累加。
     */
    void handleOrderCreated(Long messageId, String orderSn);

    /**
     * 对账任务专用：释放一个不该再占着的抢购名额（Redis 有记录但数据库没有的孤儿记录、
     * 或者超时未选地址被判定放弃的记录）——库存 INCR 加回去，抢购名单里删掉这个人。
     */
    void releaseRedisHold(Long relationId, Long memberId);

    /**
     * 对账任务专用：状态还在 PENDING、地址已经有了、但卡了很久没发出去
     * （大概率是发 MQ 之前进程崩了）——重新尝试发一次。这条记录在 Redis 里的
     * 库存/名单持有权从来没被释放过，所以补发失败时的处理跟正常抢购失败一致：
     * 标记发送失败 + 释放 Redis 名额，不会造成库存被多算。
     */
    boolean resendPendingMessage(SeckillLocalMessageEntity message);

    /**
     * 对账任务专用：状态已经是 SENT（MQ confirm 真的成功过），但迟迟没等到
     * mall-order 回填 order_sn——重新发一遍，mall-order 那边靠 orderSn 幂等，
     * 不会重复建单。这里失败了也不回滚 Redis/不降级成发送失败：因为这条记录
     * 曾经真实地被 broker 确认收下过，不能因为这次补发超时就当它从没发出去过。
     */
    void resendSentMessage(SeckillLocalMessageEntity message);

    /**
     * 收到 activate() 广播的 Redis Pub/Sub 消息后调用，清掉这个 pod 自己进程内的
     * 本地"已售罄"标记。每个 pod 的标记只存在自己内存里、互相看不见，activate()
     * 只能直接清掉处理这次请求的那一个 pod，其他 pod 得靠这条广播才会知道。
     */
    void clearLocalSoldOutFlag(Long relationId);
}

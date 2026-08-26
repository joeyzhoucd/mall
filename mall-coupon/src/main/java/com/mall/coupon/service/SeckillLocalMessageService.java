package com.mall.coupon.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.coupon.entity.SeckillLocalMessageEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;

public interface SeckillLocalMessageService extends IService<SeckillLocalMessageEntity> {

    /**
     * 抢购成功后写一行"待发送"记录。member_id+relation_id 有唯一索引兜底——
     * 同一个用户对同一场秒杀重复调用，第二次会因为违反唯一约束抛异常，
     * 上层据此判断是重复请求还是真正的第一次。
     */
    SeckillLocalMessageEntity createPending(Long relationId, Long memberId, String username, Long skuId, String skuName,
                                             String skuPic, BigDecimal seckillPrice, Long addrId);

    /**
     * 查这个用户在这场秒杀里是否已经有记录——抢购前先查一次，
     * 用来判断是全新抢购、还是上次发MQ失败后允许的重试。
     */
    SeckillLocalMessageEntity getByRelationAndMember(Long relationId, Long memberId);

    void markSent(Long id);

    void markSendFailed(Long id);

    /**
     * 抢购时没有默认地址，用户在确认页选完地址后回填。只在 doGrab() 的
     * SEND_FAILED 重试分支里用——那个分支不会跟对账任务的"超时未选地址判定放弃"
     * 撞车（对账只处理 PENDING 状态），所以不需要带条件更新。
     */
    void updateAddr(Long id, Long addrId);

    /**
     * submitAddress() 专用：带 status=PENDING 条件的回填地址。跟对账任务的
     * expireAbandonedPending 存在真实竞态——如果这条记录在用户提交地址的同时
     * 被对账任务判定"超时未选地址"过期掉，这次更新会因为状态已经不是 PENDING
     * 而失效，返回 false，调用方据此拒绝继续建单，不会出现"库存已经被对账任务
     * 放出去、但这个用户还是照样建了单"的重复售出。
     */
    boolean updateAddrIfPending(Long id, Long addrId);

    /**
     * 消费者建单成功后回填 order_sn，状态置为已生成订单。
     */
    void markOrderCreated(Long id, String orderSn);

    /**
     * 对账任务用：状态还是"待发送"、已经有地址、但超过宽限期还没发出去——
     * 大概率是发 MQ 之前进程就崩了，需要补发一次。
     */
    List<SeckillLocalMessageEntity> findStaleReadyToSend(Date updatedBefore);

    /**
     * 对账任务用：状态还是"待发送"、地址一直是空、超过很久（对齐订单超时时长）——
     * 大概率是用户抢到之后就没再回来选地址，判定放弃。
     */
    List<SeckillLocalMessageEntity> findAbandonedPending(Date updatedBefore);

    /**
     * 对账任务用：已经发过 MQ（confirm 成功过）但一直没等到消费者回填 order_sn——
     * 消息确实进了 broker，但不确定 mall-order 有没有真的处理，补发一次无害
     * （mall-order 那边靠 orderSn 幂等）。
     */
    List<SeckillLocalMessageEntity> findStaleSent(Date updatedBefore);

    /**
     * 对账任务用：把一条"待发送、一直没地址"的记录标记为过期，
     * 带 status=PENDING AND addr_id IS NULL 的条件更新——如果这期间用户自己已经
     * 选完地址（哪怕状态还没来得及变成 SENT），这次更新也会因为 addr_id 不再是
     * null 而失效，返回 false，调用方据此跳过后续的库存回滚。只带 status=PENDING
     * 不够：地址回填(updateAddr)不改状态，单靠状态判断会跟正在提交地址的用户请求
     * 撞车（对方前脚刚把地址填上，这里后脚还是能把状态改成过期，库存被错误放出去）。
     */
    boolean markExpiredIfPending(Long id);

    /**
     * 对账任务用：一次性查出这场秒杀所有已经落库的 member_id。抢购成功的用户
     * 会一直留在 Redis 的抢购名单里（不会被清掉，否则同一个人能重复抢），
     * 对账任务扫描"孤儿"记录时如果每个人都单独查一次数据库，随着这场秒杀累计
     * 卖出去的数量越来越多，这个扫描的开销会无限增长——批量查一次、在内存里
     * 做差集，扫描成本就只跟"当前这一轮 Redis 名单大小"有关，不会越滚越大。
     */
    Set<Long> getMemberIdsWithRecord(Long relationId);
}

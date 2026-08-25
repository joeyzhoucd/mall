package com.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.coupon.entity.SeckillLocalMessageEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
     * 抢购时没有默认地址，用户在确认页选完地址后回填。
     */
    void updateAddr(Long id, Long addrId);

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
     * 带 status=PENDING 的条件更新——如果这期间用户自己已经选完地址甚至建单了，
     * 这次更新会因为条件不满足而不生效，返回 false，调用方据此跳过后续的库存回滚。
     */
    boolean markExpiredIfPending(Long id);
}

package com.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.coupon.entity.SeckillLocalMessageEntity;

import java.math.BigDecimal;

public interface SeckillLocalMessageService extends IService<SeckillLocalMessageEntity> {

    /**
     * 抢购成功后写一行"待发送"记录。member_id+relation_id 有唯一索引兜底——
     * 同一个用户对同一场秒杀重复调用，第二次会因为违反唯一约束抛异常，
     * 上层据此判断是重复请求还是真正的第一次。
     */
    SeckillLocalMessageEntity createPending(Long relationId, Long memberId, Long skuId, String skuName, String skuPic,
                                             BigDecimal seckillPrice, Long addrId);

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
}

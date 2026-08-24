package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.constant.SeckillMessageStatus;
import com.mall.coupon.dao.SeckillLocalMessageDao;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.service.SeckillLocalMessageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;

@Service("seckillLocalMessageService")
public class SeckillLocalMessageServiceImpl extends ServiceImpl<SeckillLocalMessageDao, SeckillLocalMessageEntity>
        implements SeckillLocalMessageService {

    @Override
    public SeckillLocalMessageEntity createPending(Long relationId, Long memberId, Long skuId, String skuName, String skuPic,
                                                    BigDecimal seckillPrice, Long addrId) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setRelationId(relationId);
        entity.setMemberId(memberId);
        entity.setSkuId(skuId);
        entity.setSkuName(skuName);
        entity.setSkuPic(skuPic);
        entity.setSeckillPrice(seckillPrice);
        entity.setAddrId(addrId);
        entity.setStatus(SeckillMessageStatus.PENDING);
        entity.setRetryCount(0);
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        this.save(entity);
        return entity;
    }

    @Override
    public SeckillLocalMessageEntity getByRelationAndMember(Long relationId, Long memberId) {
        return this.getOne(new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("relation_id", relationId)
                .eq("member_id", memberId));
    }

    @Override
    public void markSent(Long id) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setId(id);
        entity.setStatus(SeckillMessageStatus.SENT);
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Override
    public void updateAddr(Long id, Long addrId) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setId(id);
        entity.setAddrId(addrId);
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Override
    public void markSendFailed(Long id) {
        SeckillLocalMessageEntity current = this.getById(id);
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setId(id);
        entity.setStatus(SeckillMessageStatus.SEND_FAILED);
        entity.setRetryCount((current == null || current.getRetryCount() == null ? 0 : current.getRetryCount()) + 1);
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Override
    public void markOrderCreated(Long id, String orderSn) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setId(id);
        entity.setOrderSn(orderSn);
        entity.setStatus(SeckillMessageStatus.ORDER_CREATED);
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }
}

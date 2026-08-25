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
import java.util.List;

@Service("seckillLocalMessageService")
public class SeckillLocalMessageServiceImpl extends ServiceImpl<SeckillLocalMessageDao, SeckillLocalMessageEntity>
        implements SeckillLocalMessageService {

    @Override
    public SeckillLocalMessageEntity createPending(Long relationId, Long memberId, String username, Long skuId, String skuName,
                                                    String skuPic, BigDecimal seckillPrice, Long addrId) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setRelationId(relationId);
        entity.setMemberId(memberId);
        entity.setUsername(username);
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

    @Override
    public List<SeckillLocalMessageEntity> findStaleReadyToSend(Date updatedBefore) {
        return this.list(new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("status", SeckillMessageStatus.PENDING)
                .isNotNull("addr_id")
                .lt("update_time", updatedBefore));
    }

    @Override
    public List<SeckillLocalMessageEntity> findAbandonedPending(Date updatedBefore) {
        return this.list(new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("status", SeckillMessageStatus.PENDING)
                .isNull("addr_id")
                .lt("update_time", updatedBefore));
    }

    @Override
    public List<SeckillLocalMessageEntity> findStaleSent(Date updatedBefore) {
        return this.list(new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("status", SeckillMessageStatus.SENT)
                .lt("update_time", updatedBefore));
    }

    @Override
    public boolean markExpiredIfPending(Long id) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setStatus(SeckillMessageStatus.EXPIRED);
        entity.setUpdateTime(new Date());
        // 带 status=PENDING 的条件更新：这期间如果用户自己已经把流程走完了
        // （地址填了、MQ 发了，状态不再是 PENDING），这条 UPDATE 会因为 WHERE
        // 条件不满足而影响 0 行，MyBatis-Plus 的 update(entity, wrapper) 据此
        // 返回 false——调用方看到 false 就知道"别人已经处理过了，我不该再去
        // 回滚库存"，避免对账任务跟正在进行中的正常请求打架。
        return this.update(entity, new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("id", id)
                .eq("status", SeckillMessageStatus.PENDING));
    }
}

package com.mall.coupon.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.constant.SeckillMessageStatus;
import com.mall.coupon.dao.SeckillLocalMessageDao;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.service.SeckillLocalMessageService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    public boolean updateAddrIfPending(Long id, Long addrId) {
        SeckillLocalMessageEntity entity = new SeckillLocalMessageEntity();
        entity.setAddrId(addrId);
        entity.setUpdateTime(new Date());
        return this.update(entity, new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("id", id)
                .eq("status", SeckillMessageStatus.PENDING));
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
        // 带 status=PENDING AND addr_id IS NULL 的条件更新：光判断 status=PENDING
        // 不够——updateAddr/updateAddrIfPending 回填地址时并不会改 status，如果用户
        // 正好在这个时间点提交了地址，status 还是 PENDING 但 addr_id 已经不是 null
        // 了，只看 status 会让这条过期更新照样生效，把库存错误地放出去。加上
        // addr_id IS NULL 之后，只要地址填上了（哪怕状态还没来得及变成 SENT），
        // 这次更新就会因为条件不满足而影响 0 行，返回 false。
        return this.update(entity, new QueryWrapper<SeckillLocalMessageEntity>()
                .eq("id", id)
                .eq("status", SeckillMessageStatus.PENDING)
                .isNull("addr_id"));
    }

    @Override
    public Set<Long> getMemberIdsWithRecord(Long relationId) {
        List<SeckillLocalMessageEntity> rows = this.list(new QueryWrapper<SeckillLocalMessageEntity>()
                .select("member_id")
                .eq("relation_id", relationId));
        return rows.stream().map(SeckillLocalMessageEntity::getMemberId).collect(Collectors.toSet());
    }
}

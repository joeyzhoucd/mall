package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.dao.PurchaseDao;
import com.mall.ware.dao.PurchaseDetailDao;
import com.mall.ware.entity.PurchaseDetailEntity;
import com.mall.ware.entity.PurchaseEntity;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.service.PurchaseService;
import com.mall.ware.service.WareSkuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {

    @Autowired
    private PurchaseDetailDao purchaseDetailDao;

    @Autowired
    private WareSkuService wareSkuService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<PurchaseEntity> wrapper = new QueryWrapper<>();
        
        // Filter by status
        Object status = params.get("status");
        if (status != null && !String.valueOf(status).trim().isEmpty()) {
            wrapper.eq("status", status);
        }
        
        // Filter by assignee ID
        Object assigneeId = params.get("assigneeId");
        if (assigneeId != null && !String.valueOf(assigneeId).trim().isEmpty()) {
            wrapper.eq("assignee_id", assigneeId);
        }
        
        // Search by key
        String key = (String) params.get("key");
        if (key != null && !key.trim().isEmpty()) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id);
                } catch (NumberFormatException e) {
                    w.like("assignee_name", key);
                }
            });
        }
        
        wrapper.orderByDesc("id");
        
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                wrapper
        );
        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void merge(List<Long> detailIds, Long purchaseId) {
        // Create new purchase order if not provided
        if (purchaseId == null) {
            PurchaseEntity entity = new PurchaseEntity();
            entity.setStatus(0); // New
            entity.setCreateTime(new Date());
            entity.setUpdateTime(new Date());
            this.save(entity);
            purchaseId = entity.getId();
        }
        Long finalPid = purchaseId;
        // Check if purchase order exists and status is 0/1
        PurchaseEntity target = this.getById(purchaseId);
        if (target == null || (target.getStatus() != null && target.getStatus() > 1)) {
            throw new IllegalStateException("Purchase order does not exist or cannot be merged");
        }
        // Update purchase details with purchase order ID and status=assigned(1)
        for (Long detailId : detailIds) {
            PurchaseDetailEntity d = purchaseDetailDao.selectById(detailId);
            if (d == null) continue;
            d.setPurchaseId(finalPid);
            d.setStatus(1); // Assigned
            purchaseDetailDao.updateById(d);
        }
        // Update purchase order update time
        PurchaseEntity update = new PurchaseEntity();
        update.setId(finalPid);
        update.setUpdateTime(new Date());
        this.updateById(update);
    }

    @Transactional
    @Override
    public void assign(Long purchaseId, Long assigneeId, String assigneeName, String phone) {
        PurchaseEntity entity = new PurchaseEntity();
        entity.setId(purchaseId);
        entity.setAssigneeId(assigneeId);
        entity.setAssigneeName(assigneeName);
        entity.setPhone(phone);
        entity.setStatus(1); // Assigned
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Transactional
    @Override
    public void receive(List<Long> purchaseIds, Long receiverId, String receiverName) {
        // Update purchase order status to receive(2) and update assignee
        for (Long pid : purchaseIds) {
            PurchaseEntity upd = new PurchaseEntity();
            upd.setId(pid);
            upd.setAssigneeId(receiverId);
            upd.setAssigneeName(receiverName);
            upd.setStatus(2);
            upd.setUpdateTime(new Date());
            this.updateById(upd);

            List<PurchaseDetailEntity> details = purchaseDetailDao.selectList(new QueryWrapper<PurchaseDetailEntity>().eq("purchase_id", pid));
            for (PurchaseDetailEntity d : details) {
                d.setStatus(2);
                purchaseDetailDao.updateById(d);
            }
        }
    }

    @Transactional
    @Override
    public void finish(Long purchaseId, List<Long> successDetailIds, List<Long> failedDetailIds) {
        // Update successful details to completed(3) and add stock
        if (successDetailIds != null) {
            for (Long did : successDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(3);
                purchaseDetailDao.updateById(d);
                // Add stock
                wareSkuService.addStock(d.getSkuId(), d.getWareId(), d.getSkuNum(), null);
            }
        }
        // Update failed details to failed(4)
        if (failedDetailIds != null) {
            for (Long did : failedDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(4);
                purchaseDetailDao.updateById(d);
            }
        }
        // Update purchase order status: if any failed then 4, otherwise 3
        PurchaseEntity upd = new PurchaseEntity();
        upd.setId(purchaseId);
        upd.setStatus((failedDetailIds != null && !failedDetailIds.isEmpty()) ? 4 : 3);
        upd.setUpdateTime(new Date());
        this.updateById(upd);
    }
}
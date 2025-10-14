package com.joeyzhoucd.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.ware.dao.PurchaseDao;
import com.joeyzhoucd.ware.dao.PurchaseDetailDao;
import com.joeyzhoucd.ware.entity.PurchaseDetailEntity;
import com.joeyzhoucd.ware.entity.PurchaseEntity;
import com.joeyzhoucd.ware.entity.WareSkuEntity;
import com.joeyzhoucd.ware.service.PurchaseService;
import com.joeyzhoucd.ware.service.WareSkuService;
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
        
        // 状态筛选
        Object status = params.get("status");
        if (status != null && !String.valueOf(status).trim().isEmpty()) {
            wrapper.eq("status", status);
        }
        
        // 分配人筛选
        Object assigneeId = params.get("assigneeId");
        if (assigneeId != null && !String.valueOf(assigneeId).trim().isEmpty()) {
            wrapper.eq("assignee_id", assigneeId);
        }
        
        // 关键词搜索
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
        // 若未指定采购单，新建一个
        if (purchaseId == null) {
            PurchaseEntity entity = new PurchaseEntity();
            entity.setStatus(0); // 新建
            entity.setCreateTime(new Date());
            entity.setUpdateTime(new Date());
            this.save(entity);
            purchaseId = entity.getId();
        }
        Long finalPid = purchaseId;
        // 仅允许合并到“未开始采购”的采购单（状态 0/1）
        PurchaseEntity target = this.getById(purchaseId);
        if (target == null || (target.getStatus() != null && target.getStatus() > 1)) {
            throw new IllegalStateException("只能合并到未开始采购的采购单");
        }
        // 更新明细的采购单ID与状态=已分配(1)
        for (Long detailId : detailIds) {
            PurchaseDetailEntity d = purchaseDetailDao.selectById(detailId);
            if (d == null) continue;
            d.setPurchaseId(finalPid);
            d.setStatus(1); // 已分配
            purchaseDetailDao.updateById(d);
        }
        // 更新采购单更新时间
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
        entity.setStatus(1); // 已分配
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Transactional
    @Override
    public void receive(List<Long> purchaseIds, Long receiverId, String receiverName) {
        // 将采购单状态置为 已领取(2)，明细置为 采购中(2)
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
        // 完成成功项：明细=完成(3)，并入库
        if (successDetailIds != null) {
            for (Long did : successDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(3);
                purchaseDetailDao.updateById(d);
                // 入库
                wareSkuService.addStock(d.getSkuId(), d.getWareId(), d.getSkuNum(), null);
            }
        }
        // 失败项：明细=失败(4)
        if (failedDetailIds != null) {
            for (Long did : failedDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(4);
                purchaseDetailDao.updateById(d);
            }
        }
        // 采购单状态：若存在失败则置为4，否则3
        PurchaseEntity upd = new PurchaseEntity();
        upd.setId(purchaseId);
        upd.setStatus((failedDetailIds != null && !failedDetailIds.isEmpty()) ? 4 : 3);
        upd.setUpdateTime(new Date());
        this.updateById(upd);
    }
}
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseServiceImpl.class);

    /**
     * 采购明细的状态取值。
     *
     * 这些数字原本散在代码里，只有 {@code // Assigned} 之类的英文注释标着。
     * 这里只把<b>本次改动读到的那几个</b>提出来命名 ——
     * 幂等判断依赖「3 到底是什么意思」，写成裸数字的话，
     * 下一个人很难确定 {@code d.getStatus() != 3} 是在防什么。
     * 其余散落的数字没有一并替换：那属于另一件事，不该混在这次修复里。
     */
    static final int DETAIL_ASSIGNED = 1;
    static final int DETAIL_RECEIVED = 2;
    /** 已完成 —— <b>库存已经加过了</b>。幂等判断就是靠这个值。 */
    static final int DETAIL_FINISHED = 3;
    /** 采购失败 —— 库存<b>没有</b>加过，所以允许改判成功后再入库。 */
    static final int DETAIL_FAILED = 4;

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

                // 【明细必须属于这张采购单】
                // 原来不校验，于是传别的采购单的明细 id 进来，一样会给它加库存。
                // 这不是理论问题：完成采购的请求体是前端拼的一串 id，
                // 拼错、或者两个标签页各自完成不同的单子，就会串到一起。
                if (d.getPurchaseId() == null || !d.getPurchaseId().equals(purchaseId)) {
                    log.warn("完成采购：明细 {} 不属于采购单 {}（它属于 {}），已跳过",
                            did, purchaseId, d.getPurchaseId());
                    continue;
                }

                // 【幂等：已经完成过的明细绝不能再加一次库存】
                // 原来没有这个判断。finish 是 @Transactional 的，
                // 但事务只保证「一次调用要么全成要么全不成」，
                // 【挡不住第二次调用】—— 两次成功调用各自提交，库存就加了两遍。
                //
                // 现实里触发它太容易了：界面上双击「完成」、网络超时后重试、
                // 或者两个人同时点。而结果是仓库库存虚增，
                // 且没有任何报错 —— 要等到盘点或超卖时才发现。
                //
                // 只跳过 3（已完成、库存已加）。状态 4（失败、库存没加）要允许改判成功，
                // 否则「先标失败、核实后改成功」这条正常路径就走不通了。
                if (d.getStatus() != null && d.getStatus() == DETAIL_FINISHED) {
                    log.info("完成采购：明细 {} 已经是完成状态，跳过重复入库", did);
                    continue;
                }

                d.setStatus(DETAIL_FINISHED);
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

                // 归属校验和上面同理。
                if (d.getPurchaseId() == null || !d.getPurchaseId().equals(purchaseId)) {
                    log.warn("完成采购：明细 {} 不属于采购单 {}（它属于 {}），已跳过",
                            did, purchaseId, d.getPurchaseId());
                    continue;
                }

                // 【已完成的明细不能被改判成失败】
                // 它的库存已经加进仓库了，而标成失败并不会把库存减回去 ——
                // 结果是「这条明细显示采购失败，但仓库里凭空多了一批货」，
                // 对不上账，而且没有任何报错。
                // 真要撤销入库，那是一次独立的库存调整，不能靠改状态实现。
                if (d.getStatus() != null && d.getStatus() == DETAIL_FINISHED) {
                    log.warn("完成采购：明细 {} 已完成入库，拒绝改判为失败（库存不会自动退回）", did);
                    continue;
                }

                d.setStatus(DETAIL_FAILED);
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
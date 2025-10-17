package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
        
        // çŠ¶æ€ç­›é€‰
        Object status = params.get("status");
        if (status != null && !String.valueOf(status).trim().isEmpty()) {
            wrapper.eq("status", status);
        }
        
        // åˆ†é…äººç­›é€‰
        Object assigneeId = params.get("assigneeId");
        if (assigneeId != null && !String.valueOf(assigneeId).trim().isEmpty()) {
            wrapper.eq("assignee_id", assigneeId);
        }
        
        // å…³é”®è¯æœç´¢
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
        // è‹¥æœªæŒ‡å®šé‡‡è´­å•ï¼Œæ–°å»ºä¸€ä¸ª
        if (purchaseId == null) {
            PurchaseEntity entity = new PurchaseEntity();
            entity.setStatus(0); // æ–°å»º
            entity.setCreateTime(new Date());
            entity.setUpdateTime(new Date());
            this.save(entity);
            purchaseId = entity.getId();
        }
        Long finalPid = purchaseId;
        // ä»…å…è®¸åˆå¹¶åˆ°â€œæœªå¼€å§‹é‡‡è´­â€çš„é‡‡è´­å•ï¼ˆçŠ¶æ€ 0/1ï¼‰
        PurchaseEntity target = this.getById(purchaseId);
        if (target == null || (target.getStatus() != null && target.getStatus() > 1)) {
            throw new IllegalStateException("åªèƒ½åˆå¹¶åˆ°æœªå¼€å§‹é‡‡è´­çš„é‡‡è´­å•");
        }
        // æ›´æ–°æ˜Žç»†çš„é‡‡è´­å•IDä¸ŽçŠ¶æ€=å·²åˆ†é…(1)
        for (Long detailId : detailIds) {
            PurchaseDetailEntity d = purchaseDetailDao.selectById(detailId);
            if (d == null) continue;
            d.setPurchaseId(finalPid);
            d.setStatus(1); // å·²åˆ†é…
            purchaseDetailDao.updateById(d);
        }
        // æ›´æ–°é‡‡è´­å•æ›´æ–°æ—¶é—´
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
        entity.setStatus(1); // å·²åˆ†é…
        entity.setUpdateTime(new Date());
        this.updateById(entity);
    }

    @Transactional
    @Override
    public void receive(List<Long> purchaseIds, Long receiverId, String receiverName) {
        // å°†é‡‡è´­å•çŠ¶æ€ç½®ä¸º å·²é¢†å–(2)ï¼Œæ˜Žç»†ç½®ä¸º é‡‡è´­ä¸­(2)
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
        // å®ŒæˆæˆåŠŸé¡¹ï¼šæ˜Žç»†=å®Œæˆ(3)ï¼Œå¹¶å…¥åº“
        if (successDetailIds != null) {
            for (Long did : successDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(3);
                purchaseDetailDao.updateById(d);
                // å…¥åº“
                wareSkuService.addStock(d.getSkuId(), d.getWareId(), d.getSkuNum(), null);
            }
        }
        // å¤±è´¥é¡¹ï¼šæ˜Žç»†=å¤±è´¥(4)
        if (failedDetailIds != null) {
            for (Long did : failedDetailIds) {
                PurchaseDetailEntity d = purchaseDetailDao.selectById(did);
                if (d == null) continue;
                d.setStatus(4);
                purchaseDetailDao.updateById(d);
            }
        }
        // é‡‡è´­å•çŠ¶æ€ï¼šè‹¥å­˜åœ¨å¤±è´¥åˆ™ç½®ä¸º4ï¼Œå¦åˆ™3
        PurchaseEntity upd = new PurchaseEntity();
        upd.setId(purchaseId);
        upd.setStatus((failedDetailIds != null && !failedDetailIds.isEmpty()) ? 4 : 3);
        upd.setUpdateTime(new Date());
        this.updateById(upd);
    }
}

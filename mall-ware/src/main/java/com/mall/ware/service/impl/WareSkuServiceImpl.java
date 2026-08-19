package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.constant.OrderStatus;
import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.common.to.StockDeductTo;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.common.to.StockReleaseTo;
import com.mall.ware.dao.WareSkuDao;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.entity.WareOrderTaskEntity;
import com.mall.ware.feign.OrderFeignService;
import com.mall.ware.service.WareSkuService;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.vo.OrderItemLockVo;
import com.mall.ware.vo.StockFailVo;
import com.mall.ware.vo.WareSkuLockVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mall.common.utils.RUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.mall.common.constant.MqConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    private WareOrderTaskService wareOrderTaskService;

    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        // Search by warehouse ID, SKU ID, or SKU name
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id);
                } catch (NumberFormatException e) {
                    // If not a number, search by SKU name
                    w.like("sku_name", key);
                }
            });
        }

        // Filter by warehouse ID
        Object wareId = params.get("wareId");
        if (wareId != null) {
            wrapper.eq("ware_id", wareId);
        }

        // Filter by stock status
        Object stockStatus = params.get("stockStatus");
        if (stockStatus != null) {
            if ("inStock".equals(stockStatus)) {
                wrapper.gt("stock", 0);
            } else if ("outOfStock".equals(stockStatus)) {
                wrapper.eq("stock", 0);
            } else if ("lowStock".equals(stockStatus)) {
                wrapper.lt("stock", 10); // Low stock threshold 10
            }
        }

        wrapper.orderByDesc("id"); // Sort by ID descending

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum, String skuName) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("sku_id", skuId).eq("ware_id", wareId);
        WareSkuEntity exist = this.getOne(wrapper);
        if (exist == null) {
            WareSkuEntity entity = new WareSkuEntity();
            entity.setSkuId(skuId);
            entity.setWareId(wareId);
            entity.setStock(skuNum == null ? 0 : skuNum);
            entity.setSkuName(skuName);
            entity.setStockLocked(0);
            this.save(entity);
        } else {
            int newStock = (exist.getStock() == null ? 0 : exist.getStock()) + (skuNum == null ? 0 : skuNum);
            exist.setStock(newStock);
            this.updateById(exist);
        }
    }

    @Override
    @Transactional
    public boolean orderLockStock(WareSkuLockVo lockVo) {
        if (lockVo == null || lockVo.getLocks() == null || lockVo.getLocks().isEmpty()) {
            return true;
        }
        WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
        taskEntity.setOrderSn(lockVo.getOrderSn());
        taskEntity.setCreateTime(new java.util.Date());
        wareOrderTaskService.save(taskEntity);
        List<WareOrderTaskDetailEntity> lockedDetails = new ArrayList<>();
        List<LockedSku> lockedWares = new ArrayList<>();
        for (OrderItemLockVo item : lockVo.getLocks()) {
            if (item == null || item.getSkuId() == null || item.getCount() == null) {
                continue;
            }
            List<WareSkuEntity> wareSkus = this.list(new QueryWrapper<WareSkuEntity>().eq("sku_id", item.getSkuId()));
            WareSkuEntity matched = null;
            for (WareSkuEntity wareSku : wareSkus) {
                int stock = wareSku.getStock() == null ? 0 : wareSku.getStock();
                int locked = wareSku.getStockLocked() == null ? 0 : wareSku.getStockLocked();
                if (stock - locked >= item.getCount()) {
                    matched = wareSku;
                    break;
                }
            }
            if (matched == null) {
                rollbackLockedItems(lockedDetails, lockedWares);
                return false;
            }
            int locked = matched.getStockLocked() == null ? 0 : matched.getStockLocked();
            matched.setStockLocked(locked + item.getCount());
            this.updateById(matched);

            WareOrderTaskDetailEntity detailEntity = new WareOrderTaskDetailEntity();
            detailEntity.setSkuId(item.getSkuId());
            detailEntity.setSkuName(item.getTitle());
            detailEntity.setSkuNum(item.getCount());
            detailEntity.setTaskId(taskEntity.getId());
            detailEntity.setLockStatus(StockLockStatus.LOCKED);
            wareOrderTaskDetailService.save(detailEntity);
            lockedDetails.add(detailEntity);
            lockedWares.add(new LockedSku(matched.getId(), item.getCount()));
        }
        return true;
    }

    private void rollbackLockedItems(List<WareOrderTaskDetailEntity> lockedDetails, List<LockedSku> lockedWares) {
        for (LockedSku lockedSku : lockedWares) {
            if (lockedSku == null || lockedSku.getWareSkuId() == null || lockedSku.getCount() == null) {
                continue;
            }
            WareSkuEntity current = this.getById(lockedSku.getWareSkuId());
            if (current == null) {
                continue;
            }
            int locked = current.getStockLocked() == null ? 0 : current.getStockLocked();
            int newLocked = locked - lockedSku.getCount();
            if (newLocked < 0) {
                newLocked = 0;
            }
            current.setStockLocked(newLocked);
            this.updateById(current);
        }
        for (WareOrderTaskDetailEntity detail : lockedDetails) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            detail.setLockStatus(StockLockStatus.UNLOCKED);
            wareOrderTaskDetailService.updateById(detail);
        }
    }

    private static class LockedSku {
        private final Long wareSkuId;
        private final Integer count;

        private LockedSku(Long wareSkuId, Integer count) {
            this.wareSkuId = wareSkuId;
            this.count = count;
        }

        public Long getWareSkuId() {
            return wareSkuId;
        }

        public Integer getCount() {
            return count;
        }
    }

    @Override
    public void unlockStock(StockReleaseTo releaseTo) {
        if (releaseTo == null || releaseTo.getItems() == null || releaseTo.getItems().isEmpty()) {
            return;
        }
        for (StockReleaseItemTo item : releaseTo.getItems()) {
            if (item != null && item.getOrderSn() == null) {
                item.setOrderSn(releaseTo.getOrderSn());
            }
            unlockStock(item);
        }
    }

    @Override
    public void unlockStock(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        // 0:新建,1:已支付,4:已关闭
        if (status != OrderStatus.CLOSED) {
            return;
        }
        unlockStockByDetail(itemTo, detailEntity);
        }

    @Override
    public void deductStock(StockDeductTo deductTo) {
        if (deductTo == null || deductTo.getItems() == null || deductTo.getItems().isEmpty()) {
            return;
        }
        for (StockReleaseItemTo item : deductTo.getItems()) {
            if (item != null && item.getOrderSn() == null) {
                item.setOrderSn(deductTo.getOrderSn());
            }
            deductStockItem(item);
        }
    }

    private void deductStockItem(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        // 0:新建,1:已支付,4:已关闭
        if (status != OrderStatus.PAYED) {
            return;
        }
        deductStockByDetail(itemTo, detailEntity);

        com.mall.common.to.OrderOperateTo operateTo = new com.mall.common.to.OrderOperateTo();
        operateTo.setOrderSn(itemTo.getOrderSn());
        operateTo.setStatus(OrderStatus.PAYED);
        operateTo.setNote("库存扣减成功");
        operateTo.setOperateMan("system");
        orderFeignService.recordOperate(operateTo);
    }

    @Override
    public void retryStockOps(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        if (status == OrderStatus.CLOSED) {
            unlockStockByDetail(itemTo, detailEntity);
            return;
        }
        if (status == OrderStatus.PAYED) {
            deductStockByDetail(itemTo, detailEntity);
        }
    }

    @Override
    public boolean manualRetryFailed(Long taskDetailId) {
        if (taskDetailId == null) {
            return false;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getById(taskDetailId);
        if (detailEntity == null || detailEntity.getLockStatus() == null) {
            return false;
        }
        if (!StockLockStatus.FAILED.equals(detailEntity.getLockStatus())) {
            return false;
        }
        detailEntity.setRetryCount(0);
        detailEntity.setLockStatus(StockLockStatus.LOCKED);
        wareOrderTaskDetailService.updateById(detailEntity);

        WareOrderTaskEntity taskEntity = wareOrderTaskService.getById(detailEntity.getTaskId());
        if (taskEntity == null || taskEntity.getOrderSn() == null) {
            return false;
        }
        StockReleaseItemTo itemTo = new StockReleaseItemTo();
        itemTo.setOrderSn(taskEntity.getOrderSn());
        itemTo.setSkuId(detailEntity.getSkuId());
        itemTo.setCount(detailEntity.getSkuNum());
        retryStockOps(itemTo);
        return true;
    }

    @Override
    public List<StockFailVo> listFailedDetails() {
        List<WareOrderTaskDetailEntity> details = wareOrderTaskDetailService.listByLockStatus(StockLockStatus.FAILED);
        if (details == null || details.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<StockFailVo> result = new ArrayList<>();
        for (WareOrderTaskDetailEntity detail : details) {
            StockFailVo vo = new StockFailVo();
            vo.setTaskDetailId(detail.getId());
            vo.setSkuId(detail.getSkuId());
            vo.setSkuNum(detail.getSkuNum());
            vo.setLockStatus(detail.getLockStatus());
            vo.setRetryCount(detail.getRetryCount());
            WareOrderTaskEntity task = wareOrderTaskService.getById(detail.getTaskId());
            if (task != null) {
                vo.setOrderSn(task.getOrderSn());
            }
            result.add(vo);
        }
        return result;
    }

    private Integer fetchOrderStatusWithRetry(WareOrderTaskDetailEntity detailEntity, String orderSn) {
        try {
            com.mall.common.utils.R orderResp = orderFeignService.getOrderStatus(orderSn);
            Integer code = RUtils.getCode(orderResp);
            if (code != null && code.equals(ErrorCode.ORDER_NOT_FOUND.getCode())) {
                return OrderStatus.CLOSED;
            }
            if (!RUtils.isOk(orderResp)) {
                throw new RuntimeException("order status query failed");
            }
            Integer status = RUtils.getInteger(orderResp, ResponseKeys.STATUS);
            if (status == null) {
                throw new RuntimeException("order status invalid");
            }
            return status;
        } catch (RuntimeException ex) {
            int retry = increaseRetry(detailEntity);
            if (retry < StockConstants.RETRY_LIMIT) {
                throw ex;
            }
            return null;
        }
    }

    private int increaseRetry(WareOrderTaskDetailEntity detailEntity) {
        int current = detailEntity.getRetryCount() == null ? 0 : detailEntity.getRetryCount();
        int next = current + 1;
        detailEntity.setRetryCount(next);
        if (next >= StockConstants.RETRY_LIMIT) {
            detailEntity.setLockStatus(StockLockStatus.FAILED);
            sendStockFailMessage(detailEntity);
        }
        wareOrderTaskDetailService.updateById(detailEntity);
        return next;
    }

    private void sendStockFailMessage(WareOrderTaskDetailEntity detailEntity) {
        if (detailEntity == null) {
            return;
        }
        rabbitTemplate.convertAndSend(
                MqConstants.STOCK_RELEASE_EXCHANGE,
                MqConstants.STOCK_FAIL_ROUTING_KEY,
                detailEntity
        );
    }

    private void unlockStockByDetail(StockReleaseItemTo itemTo, WareOrderTaskDetailEntity detailEntity) {
        List<WareSkuEntity> wareSkus = this.list(new QueryWrapper<WareSkuEntity>().eq("sku_id", itemTo.getSkuId()));
        if (wareSkus == null || wareSkus.isEmpty()) {
            return;
        }
        WareSkuEntity target = wareSkus.get(0);
        int locked = target.getStockLocked() == null ? 0 : target.getStockLocked();
        int newLocked = locked - itemTo.getCount();
        if (newLocked < 0) {
            newLocked = 0;
        }
        target.setStockLocked(newLocked);
        this.updateById(target);
        detailEntity.setLockStatus(StockLockStatus.UNLOCKED);
        wareOrderTaskDetailService.updateById(detailEntity);
    }

    private void deductStockByDetail(StockReleaseItemTo itemTo, WareOrderTaskDetailEntity detailEntity) {
        List<WareSkuEntity> wareSkus = this.list(new QueryWrapper<WareSkuEntity>().eq("sku_id", itemTo.getSkuId()));
        if (wareSkus == null || wareSkus.isEmpty()) {
            return;
        }
        WareSkuEntity target = wareSkus.get(0);
        int locked = target.getStockLocked() == null ? 0 : target.getStockLocked();
        int stock = target.getStock() == null ? 0 : target.getStock();
        int newLocked = locked - itemTo.getCount();
        int newStock = stock - itemTo.getCount();
        if (newLocked < 0) {
            newLocked = 0;
        }
        if (newStock < 0) {
            newStock = 0;
        }
        target.setStockLocked(newLocked);
        target.setStock(newStock);
        this.updateById(target);
        detailEntity.setLockStatus(StockLockStatus.DEDUCTED);
        wareOrderTaskDetailService.updateById(detailEntity);
    }
}
package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.PurchaseEntity;

import java.util.List;
import java.util.Map;

public interface PurchaseService extends IService<PurchaseEntity> {

    PageUtils queryPage(Map<String, Object> params);
    void merge(java.util.List<Long> detailIds, Long purchaseId);
    void assign(Long purchaseId, Long assigneeId, String assigneeName, String phone);
    void receive(java.util.List<Long> purchaseIds, Long receiverId, String receiverName);
    void finish(Long purchaseId, java.util.List<Long> successDetailIds, java.util.List<Long> failedDetailIds);
}

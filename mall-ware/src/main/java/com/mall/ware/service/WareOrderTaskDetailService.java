package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareOrderTaskDetailEntity;

import java.util.List;
import java.util.Map;


public interface WareOrderTaskDetailService extends IService<WareOrderTaskDetailEntity> {

    PageUtils queryPage(Map<String, Object> params);

    WareOrderTaskDetailEntity getByTaskIdAndSkuId(Long taskId, Long skuId);

    /**
     * 补偿任务要处理的明细：指定状态、重试未到上限、且所属任务在 {@code lockedBefore} 之前创建。
     * 最后一个条件是宽限期，见 StockRetryScheduler。
     */
    List<WareOrderTaskDetailEntity> listRetryingDetails(Integer lockStatus, Integer retryLimit, java.util.Date lockedBefore);

    List<WareOrderTaskDetailEntity> listByLockStatus(Integer lockStatus);
}

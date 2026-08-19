package com.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareOrderTaskDetailEntity;

import java.util.List;
import java.util.Map;


public interface WareOrderTaskDetailService extends IService<WareOrderTaskDetailEntity> {

    PageUtils queryPage(Map<String, Object> params);

    WareOrderTaskDetailEntity getByTaskIdAndSkuId(Long taskId, Long skuId);

    List<WareOrderTaskDetailEntity> listRetryingDetails(Integer lockStatus, Integer retryLimit);

    List<WareOrderTaskDetailEntity> listByLockStatus(Integer lockStatus);
}

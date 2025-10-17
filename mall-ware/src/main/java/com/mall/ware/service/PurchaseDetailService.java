package com.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.PurchaseDetailEntity;

import java.util.Map;

public interface PurchaseDetailService extends IService<PurchaseDetailEntity> {
    PageUtils queryPage(Map<String, Object> params);
}


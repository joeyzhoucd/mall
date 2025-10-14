package com.joeyzhoucd.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.ware.entity.PurchaseDetailEntity;

import java.util.Map;

public interface PurchaseDetailService extends IService<PurchaseDetailEntity> {
    PageUtils queryPage(Map<String, Object> params);
}


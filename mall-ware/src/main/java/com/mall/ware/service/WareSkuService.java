package com.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareSkuEntity;

import java.util.Map;


public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    
    void addStock(Long skuId, Long wareId, Integer skuNum, String skuName);
}

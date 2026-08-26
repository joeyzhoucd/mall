package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.vo.WareSkuLockVo;
import com.mall.common.to.StockReleaseTo;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.common.to.StockDeductTo;

import java.util.Map;


public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    
    void addStock(Long skuId, Long wareId, Integer skuNum, String skuName);

    boolean orderLockStock(WareSkuLockVo lockVo);

    void unlockStock(StockReleaseTo releaseTo);

    void unlockStock(StockReleaseItemTo itemTo);

    void deductStock(StockDeductTo deductTo);

    void retryStockOps(StockReleaseItemTo itemTo);

    boolean manualRetryFailed(Long taskDetailId);

    java.util.List<com.mall.ware.vo.StockFailVo> listFailedDetails();
}

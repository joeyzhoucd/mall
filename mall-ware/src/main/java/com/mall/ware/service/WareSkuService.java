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

    /**
     * 后台直接设置某个 SKU 在某个仓库的库存（不是增量，是设成这个值）。
     *
     * @param wareId 可以为 null。为 null 时由实现推断仓库，推断不出来会抛异常
     *               说明原因，而不是随便挑一个仓 —— 见实现里的注释。
     * @return 实际写入的仓库 id
     */
    Long setStock(Long skuId, Long wareId, Integer stock);

}

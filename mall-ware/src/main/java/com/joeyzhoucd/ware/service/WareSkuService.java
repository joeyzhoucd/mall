package com.joeyzhoucd.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.ware.entity.WareSkuEntity;

import java.util.Map;

/**
 * 商品库存
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 采购入库：若不存在则插入，存在则累加
     */
    void addStock(Long skuId, Long wareId, Integer skuNum, String skuName);
}
package com.mall.ware.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareSkuEntity;

import java.util.Map;

/**
 * å•†å“åº“å­˜
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface WareSkuService extends IService<WareSkuEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * é‡‡è´­å…¥åº“ï¼šè‹¥ä¸å­˜åœ¨åˆ™æ’å…¥ï¼Œå­˜åœ¨åˆ™ç´¯åŠ 
     */
    void addStock(Long skuId, Long wareId, Integer skuNum, String skuName);
}

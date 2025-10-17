package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.vo.SkuInfoVo;

import java.util.Map;

/**
 * skuä¿¡æ¯
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
    /**
     * æŸ¥è¯¢SKUä¿¡æ¯åˆ—è¡¨ï¼ˆåŒ…å«å…³è”æ•°æ®ï¼‰
     */
    PageUtils queryPageWithDetails(Map<String, Object> params);
}


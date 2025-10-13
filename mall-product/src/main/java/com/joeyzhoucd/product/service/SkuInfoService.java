package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.SkuInfoEntity;
import com.joeyzhoucd.product.vo.SkuInfoVo;

import java.util.Map;

/**
 * sku信息
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
    /**
     * 查询SKU信息列表（包含关联数据）
     */
    PageUtils queryPageWithDetails(Map<String, Object> params);
}


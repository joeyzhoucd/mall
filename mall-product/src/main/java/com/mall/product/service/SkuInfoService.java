package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.vo.SkuInfoVo;

import java.util.Map;


public interface SkuInfoService extends IService<SkuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);
    
    
    PageUtils queryPageWithDetails(Map<String, Object> params);

    com.mall.product.vo.SkuItemVo item(Long skuId);
}

package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SkuBaseAttrValueEntity;

import java.util.Map;


public interface SkuBaseAttrValueService extends IService<SkuBaseAttrValueEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

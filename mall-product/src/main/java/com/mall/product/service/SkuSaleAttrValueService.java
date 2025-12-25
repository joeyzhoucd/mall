package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SkuSaleAttrValueEntity;

import java.util.Map;


public interface SkuSaleAttrValueService extends IService<SkuSaleAttrValueEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 返回某 sku 的销售属性值列表（字符串）
     */
    java.util.List<String> getSkuSaleAttrValuesAsStringList(Long skuId);
}
